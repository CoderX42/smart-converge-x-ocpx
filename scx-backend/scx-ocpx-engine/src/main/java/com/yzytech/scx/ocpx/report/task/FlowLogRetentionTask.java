package com.yzytech.scx.ocpx.report.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.yzytech.scx.ocpx.persistence.mapper.FlowLogRetentionSqlMapper;
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

	private final FlowLogRetentionSqlMapper sqlMapper;

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
			int details = drain(() -> sqlMapper.deleteExpiredDetails(limit()));
			int feedback = drain(() -> sqlMapper.deleteExpiredFeedback(limit()));
			int callbacks = drain(() -> sqlMapper.deleteExpiredCallbacks(limit()));
			int previews = clearExpiredPreviews();
			int metadata = drain(() -> sqlMapper.deleteExpiredMetadata(limit()));
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
		if (!previewRetentionReady()) {
			log.warn("skip flow-log preview retention: indexed presence flag is unavailable");
			return 0;
		}
		int total = 0;
		PreviewCursor cursor = null;
		for (int round = 0; round < safeMaxBatches(); round++) {
			List<PreviewRow> rows = sqlMapper
				.selectExpiredPreviews(cursor == null ? null : cursor.eventTime(), cursor == null ? null : cursor.id(),
						limit())
				.stream()
				.map(rs -> new PreviewRow(rs.id(), rs.eventTime().toLocalDateTime()))
				.toList();
			if (rows.isEmpty()) {
				break;
			}
			List<Object> ids = new ArrayList<>(rows.stream().map(PreviewRow::id).toList());
			total += sqlMapper.clearPreviews(ids);
			PreviewRow last = rows.get(rows.size() - 1);
			cursor = new PreviewCursor(last.eventTime(), last.id());
			if (rows.size() < limit()) {
				break;
			}
			pause();
		}
		return total;
	}

	private boolean previewRetentionReady() {
		Integer columns = sqlMapper.selectPreviewRetentionReadyInformationSchema();
		return columns != null && columns == 3;
	}

	private record PreviewRow(String id, LocalDateTime eventTime) {
	}

	private record PreviewCursor(LocalDateTime eventTime, String id) {
	}

	private int drain(java.util.function.IntSupplier deleteBatch) {
		int total = 0;
		int changed = 0;
		int rounds = 0;
		while (rounds++ < safeMaxBatches()) {
			changed = deleteBatch.getAsInt();
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
