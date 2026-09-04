package com.yzytech.scx.ocpx.rta.persistence;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;



/** RTA 事实与小时投影必须在同一事务中提交，重投不得重复累加。 */
@Service
public class RtaDecisionFactPersistence {

	private final JdbcTemplate jdbcTemplate;

	private MeterRegistry meterRegistry;

	public RtaDecisionFactPersistence(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Autowired(required = false)
	void setMeterRegistry(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Transactional
	public void persist(RtaDecisionEvent event) {
		if (event == null || event.getRequestId() == null || event.getDecision() == null
				|| event.getReceivedAt() == null || event.getCompletedAt() == null) {
			throw new IllegalArgumentException("RTA decision event is invalid");
		}
		try {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_rta_decision_fact
					(id,request_id,request_hash,advertiser_id,promotion_link_id,media_id,monitor_task_id,
					 profile_id,adapter_code,decision,outcome_class,reason_code,latency_ms,matched_task_ids,
					 received_at,completed_at,metric_version)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", id(), event.getRequestId(), event.getRequestHash(), event.getAdvertiserId(),
					event.getPromotionLinkId(), event.getMediaId(), event.getMonitorTaskId(), event.getProfileId(),
					event.getAdapterCode(), event.getDecision().name(), event.getOutcomeClass(), event.getReasonCode(),
					event.getLatencyMs(), JSON.toJSONString(event.getMatchedTaskIds()), event.getReceivedAt(),
					event.getCompletedAt(), 1);
		}
		catch (DuplicateKeyException duplicate) {
			return;
		}
		int allow = event.getDecision() == RtaDecision.ALLOW ? 1 : 0;
		int error = "ERROR".equals(event.getOutcomeClass()) ? 1 : 0;
		int timeout = "TIMEOUT".equals(event.getOutcomeClass()) ? 1 : 0;
		LocalDateTime bucket = event.getReceivedAt().withMinute(0).withSecond(0).withNano(0);
		try {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_rta_hourly_metric
					(id,time_hour,advertiser_id,promotion_link_id,media_id,monitor_task_id,profile_id,
					 request_count,allow_count,deny_count,timeout_count,error_count,allow_rate,metric_version)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					ON DUPLICATE KEY UPDATE
					 request_count=request_count+VALUES(request_count),
					 allow_count=allow_count+VALUES(allow_count),
					 deny_count=deny_count+VALUES(deny_count),
					 timeout_count=timeout_count+VALUES(timeout_count),
					 error_count=error_count+VALUES(error_count),
					 allow_rate=IF(request_count=0,NULL,allow_count/request_count),
					 update_time=CURRENT_TIMESTAMP(3)
					""", id(), bucket, event.getAdvertiserId(), event.getPromotionLinkId(), event.getMediaId(),
					event.getMonitorTaskId(), event.getProfileId() == null ? "SYSTEM" : event.getProfileId(), 1, allow,
					1 - allow, timeout, error, allow == 1 ? 1D : 0D, 1);
		}
		catch (RuntimeException exception) {
			if (meterRegistry != null) {
				meterRegistry.counter("rta_hourly_metric_write_failures_total").increment();
			}
			throw exception;
		}
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
