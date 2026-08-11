package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.config.OcpxReportingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Turns immutable runtime contributions into the single S8 hourly fact. The aggregate and
 * contribution acknowledgement share one transaction, so retrying a failed run cannot
 * double count. The same method is used by normal incremental runs and an explicitly
 * authorised local backfill caller.
 */
@Service
@RequiredArgsConstructor
public class OcpxHourlyAggregationService {

	private static final String SOURCE = "RUNTIME_CONTRIBUTION";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	private final OcpxReportingProperties properties;

	@Scheduled(fixedDelay = 5000)
	public void scheduledAggregate() {
		if (properties.isAggregationWorkerEnabled()) {
			aggregateNextBatch("incremental");
		}
	}

	@Transactional
	public int aggregateNextBatch(String reason) {
		String runId = UUID.randomUUID().toString().replace("-", "");
		jdbcTemplate.getJdbcTemplate().update("""
				INSERT IGNORE INTO scx_ocpx_hourly_aggregation_cursor
				(source_name,partition_no,last_sequence_no,status,update_time)
				VALUES (?,0,0,'IDLE',NOW(3))
				""", SOURCE);
		List<Long> sequences = jdbcTemplate.getJdbcTemplate().queryForList("""
				SELECT sequence_no FROM scx_ocpx_hourly_fact_contribution
				WHERE aggregated_at IS NULL ORDER BY sequence_no
				LIMIT ? FOR UPDATE SKIP LOCKED
				""", Long.class, properties.getAggregationBatchSize());
		if (sequences.isEmpty()) {
			jdbcTemplate.getJdbcTemplate().update("""
					UPDATE scx_ocpx_hourly_aggregation_cursor
					SET status='IDLE',lease_owner=NULL,lease_until=NULL,last_error_code=NULL,update_time=NOW(3)
					WHERE source_name=? AND partition_no=0
					""", SOURCE);
			return 0;
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource("sequences", sequences).addValue("runId", runId);
		jdbcTemplate.update(
				"""
						INSERT INTO scx_ocpx_hourly_report
						(id,time_hour,tenant_id,timezone,channel_id,advertiser_id,product_id,app_link_id,task_id,
						 media_account_id,operator_user_id,monitor_id,route_version_id,route_version,dimension_status,
						 impressions,clicks,eligible_click_count,deduplicated_click_count,skipped_click_count,
						 classified_click_count,cost_amount,activate_count,first_activate_count,reactivation_count,
						 register_count,payment_count,order_count,first_order_count,next_stay_count,retention_3d_count,
						 retention_7d_count,raw_conversion_count,forwarded_conversion_count,deducted_conversion_count,
						 delivery_failure_count,raw_clicks,valid_clicks,duplicate_clicks,adjustment_count,deduction_count,
						 adjustment_amount,deduction_amount,source_watermark,as_of,finality,quality_status,
						 metric_definitions_version)
						SELECT LOWER(REPLACE(UUID(),'-','')),stat_hour,tenant_id,timezone,channel_id,advertiser_id,product_id,
						 app_link_id,task_id,media_account_id,operator_user_id,monitor_route_id,route_version_id,route_version,
						 'FROZEN',SUM(impressions),SUM(raw_clicks),SUM(valid_clicks),SUM(duplicate_clicks),SUM(skipped_clicks),
						 SUM(raw_clicks),SUM(cost_amount),SUM(activation_count),SUM(first_activation_count),
						 SUM(reactivation_count),SUM(registration_count),SUM(payment_count),SUM(order_count),
						 SUM(first_order_count),SUM(retention_d2_count),SUM(retention_d3_count),SUM(retention_d7_count),
						 SUM(raw_conversion_count),SUM(forwarded_conversion_count),SUM(deduction_count),
						 SUM(delivery_failure_count),SUM(raw_clicks),SUM(valid_clicks),SUM(duplicate_clicks),
						 SUM(adjustment_count),SUM(deduction_count),0.000000,0.000000,MAX(received_time),NOW(3),
						 CASE WHEN MAX(received_time)<DATE_SUB(NOW(3),INTERVAL 7 DAY) THEN 'FINAL' ELSE 'PROVISIONAL' END,
						 'COMPLETE','S8_V1'
						FROM scx_ocpx_hourly_fact_contribution WHERE sequence_no IN (:sequences)
						GROUP BY stat_hour,tenant_id,timezone,channel_id,advertiser_id,product_id,app_link_id,task_id,
						 media_account_id,operator_user_id,monitor_route_id,route_version_id,route_version
						ON DUPLICATE KEY UPDATE
						 impressions=impressions+VALUES(impressions),clicks=clicks+VALUES(clicks),
						 eligible_click_count=eligible_click_count+VALUES(eligible_click_count),
						 deduplicated_click_count=deduplicated_click_count+VALUES(deduplicated_click_count),
						 skipped_click_count=skipped_click_count+VALUES(skipped_click_count),
						 classified_click_count=classified_click_count+VALUES(classified_click_count),
						 cost_amount=cost_amount+VALUES(cost_amount),activate_count=activate_count+VALUES(activate_count),
						 first_activate_count=first_activate_count+VALUES(first_activate_count),
						 reactivation_count=reactivation_count+VALUES(reactivation_count),
						 register_count=register_count+VALUES(register_count),payment_count=payment_count+VALUES(payment_count),
						 order_count=order_count+VALUES(order_count),first_order_count=first_order_count+VALUES(first_order_count),
						 next_stay_count=next_stay_count+VALUES(next_stay_count),
						 retention_3d_count=retention_3d_count+VALUES(retention_3d_count),
						 retention_7d_count=retention_7d_count+VALUES(retention_7d_count),
						 raw_conversion_count=raw_conversion_count+VALUES(raw_conversion_count),
						 forwarded_conversion_count=forwarded_conversion_count+VALUES(forwarded_conversion_count),
						 deducted_conversion_count=deducted_conversion_count+VALUES(deducted_conversion_count),
						 delivery_failure_count=delivery_failure_count+VALUES(delivery_failure_count),
						 raw_clicks=raw_clicks+VALUES(raw_clicks),valid_clicks=valid_clicks+VALUES(valid_clicks),
						 duplicate_clicks=duplicate_clicks+VALUES(duplicate_clicks),
						 adjustment_count=adjustment_count+VALUES(adjustment_count),
						 deduction_count=deduction_count+VALUES(deduction_count),
						 source_watermark=GREATEST(COALESCE(source_watermark,'1970-01-01 00:00:00.000'),
						 	VALUES(source_watermark)),as_of=NOW(3),
						 finality=VALUES(finality),quality_status='COMPLETE',metric_definitions_version='S8_V1'
						""",
				parameters);
		jdbcTemplate.update("""
				UPDATE scx_ocpx_hourly_fact_contribution
				SET aggregated_at=NOW(3),aggregate_run_id=:runId WHERE sequence_no IN (:sequences)
				""", parameters);
		jdbcTemplate.getJdbcTemplate().update("""
				UPDATE scx_ocpx_hourly_aggregation_cursor
				SET last_sequence_no=?,source_watermark=(SELECT MAX(received_time)
				 FROM scx_ocpx_hourly_fact_contribution WHERE aggregate_run_id=?),last_run_id=?,status='IDLE',
				 lease_owner=NULL,lease_until=NULL,last_error_code=NULL,update_time=NOW(3)
				WHERE source_name=? AND partition_no=0
				""", sequences.get(sequences.size() - 1), runId, runId, SOURCE);
		return sequences.size();
	}

}
