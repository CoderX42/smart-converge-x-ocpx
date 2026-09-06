package com.yzytech.scx.ocpx.report.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** V119 链路日志保留策略：详情七天、脱敏阶段元数据九十天。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowLogRetentionTask {

	private static final String RELEASE_LOCK_SCRIPT = "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";

	private static final int DEFAULT_BATCH_SIZE = 1000;

	private final StringRedisTemplate redis;

	private final JdbcTemplate jdbcTemplate;

	@Value("${app.jobs.retention-batch-size:1000}")
	private int batchSize = DEFAULT_BATCH_SIZE;

	/**
	 * Five retention targets times two batches keeps each run at no more than ten
	 * batches.
	 */
	@Value("${app.jobs.retention-max-batches-per-target:2}")
	private int maxBatchesPerTarget = 2;

	@Value("${app.jobs.retention-batch-pause-ms:100}")
	private long batchPauseMs = 100L;

	@Scheduled(cron = "${app.jobs.retention-cron:0 0/10 * * * ?}")
	public void clean() {
		String lockKey = "rtb{flowLogRetention}";
		String ownerToken = UUID.randomUUID().toString();
		Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, ownerToken, 30, TimeUnit.MINUTES);
		if (!Boolean.TRUE.equals(acquired))
			return;
		try {
			int details = drain("DELETE FROM scx_ocpx_flow_log_detail WHERE expires_at<NOW(3) LIMIT " + limit());
			int feedback = drain("DELETE FROM scx_ocpx_log_feedback "
					+ "WHERE create_time<DATE_SUB(NOW(3),INTERVAL 7 DAY) ORDER BY create_time LIMIT " + limit());
			int callbacks = drain("DELETE FROM scx_ocpx_log_callback "
					+ "WHERE create_time<DATE_SUB(NOW(3),INTERVAL 7 DAY) ORDER BY create_time LIMIT " + limit());
			int previews = clearExpiredPreviews();
			int metadata = drain("DELETE FROM scx_ocpx_flow_log "
					+ "WHERE event_time<DATE_SUB(NOW(3),INTERVAL 90 DAY) ORDER BY event_time LIMIT " + limit());
			log.info("flow log retention completed detail:{} feedback:{} callback:{} preview:{} metadata:{}", details,
					feedback, callbacks, previews, metadata);
		}
		catch (RuntimeException exception) {
			log.error("flow log retention failed", exception);
		}
		finally {
			try {
				redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(RELEASE_LOCK_SCRIPT,
						Long.class), List.of(lockKey), ownerToken);
			}
			catch (RuntimeException releaseError) {
				log.warn("flow log retention lock release failed");
			}
		}
	}

	/**
	 * Clear old previews with a keyset walk. A plain UPDATE ... LIMIT has to rescan
	 * already-cleared rows on every run because response_preview is not indexed.
	 */
	private int clearExpiredPreviews() {
		int total = 0;
		PreviewCursor cursor = null;
		for (int round = 0; round < safeMaxBatches(); round++) {
			StringBuilder sql = new StringBuilder("""
					SELECT id,event_time
					FROM scx_ocpx_flow_log FORCE INDEX (idx_ocpx_flow_log_page)
					WHERE response_preview IS NOT NULL
					  AND event_time<DATE_SUB(NOW(3),INTERVAL 7 DAY)
					""");
			List<Object> args = new ArrayList<>();
			if (cursor != null) {
				sql.append(" AND (event_time>? OR (event_time=? AND id>?))");
				args.add(cursor.eventTime());
				args.add(cursor.eventTime());
				args.add(cursor.id());
			}
			sql.append(" ORDER BY event_time,id LIMIT ?");
			args.add(limit());
			List<PreviewRow> rows = jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
					new PreviewRow(rs.getString("id"), rs.getTimestamp("event_time").toLocalDateTime()), args.toArray());
			if (rows.isEmpty()) {
				break;
			}
			String placeholders = String.join(",", java.util.Collections.nCopies(rows.size(), "?"));
			List<Object> ids = new ArrayList<>(rows.stream().map(PreviewRow::id).toList());
			total += jdbcTemplate.update(
					"UPDATE scx_ocpx_flow_log SET response_preview=NULL WHERE id IN (" + placeholders + ")", ids.toArray());
			PreviewRow last = rows.get(rows.size() - 1);
			cursor = new PreviewCursor(last.eventTime(), last.id());
			if (rows.size() < limit()) {
				break;
			}
			pause();
		}
		return total;
	}

	private record PreviewRow(String id, LocalDateTime eventTime) {
	}

	private record PreviewCursor(LocalDateTime eventTime, String id) {
	}

	private int drain(String sql) {
		int total = 0;
		int changed = 0;
		int rounds = 0;
		while (rounds++ < safeMaxBatches()) {
			changed = jdbcTemplate.update(sql);
			total += changed;
			if (changed < safeBatchSize()) {
				break;
			}
			pause();
		}
		return total;
	}

	private int limit() {
		return safeBatchSize();
	}

	private int safeBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}

	private int safeMaxBatches() {
		return maxBatchesPerTarget > 0 ? maxBatchesPerTarget : 1;
	}

	private void pause() {
		if (batchPauseMs <= 0) {
			return;
		}
		try {
			Thread.sleep(batchPauseMs);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

}
