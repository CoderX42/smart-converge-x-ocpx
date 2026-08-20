package com.yzytech.scx.ocpx.report.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

	private static final int BATCH_SIZE = 1000;

	private final StringRedisTemplate redis;

	private final JdbcTemplate jdbcTemplate;

	@Scheduled(cron = "0 35 0 * * ?")
	public void clean() {
		String lockKey = "rtb{flowLogRetention}";
		Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.MINUTES);
		if (!Boolean.TRUE.equals(acquired))
			return;
		int details = drain("DELETE FROM scx_ocpx_flow_log_detail WHERE expires_at<NOW(3) LIMIT " + BATCH_SIZE);
		int feedback = drain("DELETE FROM scx_ocpx_log_feedback "
				+ "WHERE create_time<DATE_SUB(NOW(3),INTERVAL 7 DAY) LIMIT " + BATCH_SIZE);
		int callbacks = drain("DELETE FROM scx_ocpx_log_callback "
				+ "WHERE create_time<DATE_SUB(NOW(3),INTERVAL 7 DAY) LIMIT " + BATCH_SIZE);
		int previews = drain("UPDATE scx_ocpx_flow_log SET response_preview=NULL "
				+ "WHERE response_preview IS NOT NULL AND event_time<DATE_SUB(NOW(3),INTERVAL 7 DAY) LIMIT "
				+ BATCH_SIZE);
		int metadata = drain("DELETE FROM scx_ocpx_flow_log "
				+ "WHERE event_time<DATE_SUB(NOW(3),INTERVAL 90 DAY) LIMIT " + BATCH_SIZE);
		log.info("flow log retention completed detail:{} feedback:{} callback:{} preview:{} metadata:{}", details,
				feedback, callbacks, previews, metadata);
	}

	private int drain(String sql) {
		int total = 0;
		int changed;
		do {
			changed = jdbcTemplate.update(sql);
			total += changed;
		}
		while (changed == BATCH_SIZE);
		return total;
	}

}
