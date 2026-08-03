package com.yzytech.scx.adx.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdxConfigOutboxPublisher {

	private static final String TOPIC = "scx:ocpx:config:changed";

	private final JdbcTemplate jdbcTemplate;

	private final StringRedisTemplate redisTemplate;

	public AdxConfigOutboxPublisher(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisTemplate = redisTemplate;
	}

	@Scheduled(fixedDelay = 1000)
	public void publishPending() {
		List<ConfigEvent> events = jdbcTemplate.query("""
				SELECT id,aggregate_id,config_version,event_type
				FROM scx_ocpx_config_outbox WHERE published=0 ORDER BY create_time LIMIT 100
				""", (rs, rowNum) -> new ConfigEvent(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)));
		for (ConfigEvent event : events) {
			try {
				redisTemplate.convertAndSend(TOPIC,
						event.aggregateId() + ":" + event.configVersion() + ":" + event.eventType());
				jdbcTemplate.update(
						"UPDATE scx_ocpx_config_outbox SET published=1,published_at=NOW(3) WHERE id=? AND published=0",
						event.id());
			}
			catch (RuntimeException ignored) {
				return;
			}
		}
	}

	private record ConfigEvent(String id, String aggregateId, long configVersion, String eventType) {
	}

}
