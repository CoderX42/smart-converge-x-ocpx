package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Transactional-outbox dispatcher. Redis is only an invalidation/apply signal; database
 * distribution and instance acknowledgements remain the truth.
 */
@Component
public class OcpxConfigOutboxPublisher {

	private static final String TOPIC = "scx:ocpx:config:changed";

	private final JdbcTemplate jdbcTemplate;

	private final StringRedisTemplate redisTemplate;

	private final OcpxTrackingProperties properties;

	public OcpxConfigOutboxPublisher(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
			OcpxTrackingProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	@Scheduled(fixedDelay = 1000)
	public void publishPending() {
		if (!properties.isDistributionWorkerEnabled()) {
			return;
		}
		String owner = "outbox-" + UUID.randomUUID().toString().substring(0, 12);
		List<ConfigEvent> candidates = jdbcTemplate.query("""
				SELECT id,aggregate_id,config_version,event_type,payload_json,attempt_count
				FROM scx_ocpx_config_outbox
				WHERE published=0 AND available_at<=NOW(3)
				 AND (delivery_status IN ('PENDING','RETRY_WAIT')
				      OR (delivery_status='CLAIMED' AND lease_until<NOW(3)))
				ORDER BY create_time LIMIT 100
				""", (rs, rowNum) -> new ConfigEvent(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4),
				rs.getString(5), rs.getInt(6)));
		for (ConfigEvent event : candidates) {
			int claimed = jdbcTemplate.update("""
					UPDATE scx_ocpx_config_outbox SET delivery_status='CLAIMED',lease_owner=?,
					 lease_until=DATE_ADD(NOW(3),INTERVAL 30 SECOND),attempt_count=attempt_count+1,update_time=NOW(3)
					WHERE id=? AND published=0 AND available_at<=NOW(3)
					 AND (delivery_status IN ('PENDING','RETRY_WAIT')
					      OR (delivery_status='CLAIMED' AND lease_until<NOW(3)))
					""", owner, event.id());
			if (claimed == 0) {
				continue;
			}
			try {
				redisTemplate.convertAndSend(TOPIC,
						event.aggregateId() + ":" + event.configVersion() + ":" + event.eventType());
				jdbcTemplate.update("""
						UPDATE scx_ocpx_config_outbox SET published=1,published_at=NOW(3),delivery_status='DELIVERED',
						 lease_owner=NULL,lease_until=NULL,last_error_code=NULL,update_time=NOW(3)
						WHERE id=? AND delivery_status='CLAIMED' AND lease_owner=?
						""", event.id(), owner);
				markDistributionStarted(event.payloadJson());
			}
			catch (RuntimeException error) {
				int delaySeconds = retryDelaySeconds(event.attemptCount() + 1);
				jdbcTemplate.update(
						"""
								UPDATE scx_ocpx_config_outbox SET delivery_status='RETRY_WAIT',available_at=DATE_ADD(NOW(3),INTERVAL ? SECOND),
								 lease_owner=NULL,lease_until=NULL,last_error_code='SIGNAL_DELIVERY_FAILED',update_time=NOW(3)
								WHERE id=? AND delivery_status='CLAIMED' AND lease_owner=?
								""",
						delaySeconds, event.id(), owner);
			}
		}
	}

	static int retryDelaySeconds(int attempt) {
		return Math.min(300, 1 << Math.min(Math.max(attempt, 0), 8));
	}

	private void markDistributionStarted(String payloadJson) {
		if (payloadJson == null || payloadJson.isBlank()) {
			return;
		}
		jdbcTemplate.update("""
				UPDATE scx_ocpx_route_distribution d
				SET d.status='DISTRIBUTING',d.attempt_count=d.attempt_count+1,d.update_time=NOW(3)
				WHERE d.id=JSON_UNQUOTE(JSON_EXTRACT(?, '$.referenceId')) AND d.status='PENDING'
				""", payloadJson);
	}

	private record ConfigEvent(String id, String aggregateId, long configVersion, String eventType, String payloadJson,
			int attemptCount) {
	}

}
