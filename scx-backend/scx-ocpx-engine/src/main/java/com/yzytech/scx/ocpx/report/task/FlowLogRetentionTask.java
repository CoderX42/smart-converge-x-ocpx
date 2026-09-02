package com.yzytech.scx.ocpx.report.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** V119 链路日志保留策略：详情七天、脱敏阶段元数据九十天。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowLogRetentionTask {

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
		Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.MINUTES);
		if (!Boolean.TRUE.equals(acquired))
			return;
		try {
			int details = drain("DELETE FROM scx_ocpx_flow_log_detail WHERE expires_at<NOW(3) LIMIT " + limit());
			int feedback = drain("DELETE FROM scx_ocpx_log_feedback "
					+ "WHERE create_time<DATE_SUB(NOW(3),INTERVAL 7 DAY) ORDER BY create_time LIMIT " + limit());
			int callbacks = drain("DELETE FROM scx_ocpx_log_callback "
					+ "WHERE create_time<DATE_SUB(NOW(3),INTERVAL 7 DAY) ORDER BY create_time LIMIT " + limit());
			int previews = drain("UPDATE scx_ocpx_flow_log SET response_preview=NULL "
					+ "WHERE response_preview IS NOT NULL AND event_time<DATE_SUB(NOW(3),INTERVAL 7 DAY) LIMIT " + limit());
			int metadata = drain("DELETE FROM scx_ocpx_flow_log "
					+ "WHERE event_time<DATE_SUB(NOW(3),INTERVAL 90 DAY) ORDER BY event_time LIMIT " + limit());
			log.info("flow log retention completed detail:{} feedback:{} callback:{} preview:{} metadata:{}", details,
					feedback, callbacks, previews, metadata);
		}
		catch (RuntimeException exception) {
			log.error("flow log retention failed", exception);
		}
		finally {
			redis.unlink(lockKey);
		}
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
