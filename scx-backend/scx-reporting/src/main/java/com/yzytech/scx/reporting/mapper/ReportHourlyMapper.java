package com.yzytech.scx.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.reporting.dto.ReportBreakdown;
import com.yzytech.scx.reporting.entity.ReportHourly;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportHourlyMapper extends BaseMapper<ReportHourly> {

	@Select("""
			SELECT SHA2(CONCAT(
			  COALESCE((
			    SELECT CONCAT(COUNT(*),'|',COALESCE(MAX(e.received_at),''),'|',
			      COALESCE(SUM(CRC32(CONCAT(e.id,'|',e.payload_hash,'|',e.status))),0))
			    FROM scx_raw_event e
			    JOIN scx_tracking_task t ON t.id=e.task_id
			    WHERE t.media_id=#{mediaId} AND e.data_origin='LIVE'
			      AND e.occurred_at>=#{startUtc} AND e.occurred_at<#{endUtc}
			  ),'0'),
			  COALESCE((
			    SELECT CONCAT(COUNT(*),'|',COALESCE(MAX(a.attributed_at),''),'|',
			      COALESCE(SUM(CRC32(CONCAT(a.id,'|',a.revision,'|',a.status,'|',a.current_flag,
			        '|',a.review_status,'|',a.over_limit))),0))
			    FROM scx_attribution_result a
			    JOIN scx_raw_event e ON e.id=a.conversion_event_id
			    JOIN scx_tracking_task t ON t.id=e.task_id
			    WHERE t.media_id=#{mediaId} AND e.data_origin='LIVE'
			      AND e.occurred_at>=#{startUtc} AND e.occurred_at<#{endUtc}
			  ),'0'),
			  COALESCE((
			    SELECT CONCAT(COUNT(*),'|',COALESCE(MAX(j.update_time),''),'|',
			      COALESCE(SUM(CRC32(CONCAT(j.id,'|',j.status,'|',j.attempts))),0))
			    FROM scx_callback_job j
			    JOIN scx_attribution_result a ON a.id=j.attribution_result_id
			    JOIN scx_raw_event e ON e.id=a.conversion_event_id
			    JOIN scx_tracking_task t ON t.id=e.task_id
			    WHERE t.media_id=#{mediaId} AND e.data_origin='LIVE'
			      AND e.occurred_at>=#{startUtc} AND e.occurred_at<#{endUtc}
			  ),'0'),
			  COALESCE((
			    SELECT CONCAT(COUNT(*),'|',COALESCE(MAX(f.pulled_at),''),'|',
			      COALESCE(SUM(CRC32(f.source_hash)),0))
			    FROM scx_media_report_hourly_fact f
			    WHERE f.media_id=#{mediaId}
			      AND f.stat_hour_utc>=#{startUtc} AND f.stat_hour_utc<#{endUtc}
			  ),'0'),
			  COALESCE((
			    SELECT CONCAT(COUNT(*),'|',COALESCE(SUM(CRC32(CONCAT(
			      b.id,'|',COALESCE(b.tracking_task_id,''),'|',COALESCE(b.ad_group_id,''),
			      '|',b.binding_status,'|',b.version,'|',COALESCE(og.event_code,'')))),0))
			    FROM scx_media_publishing_binding b
			    JOIN scx_media_account ma ON ma.id=b.media_account_id
			    LEFT JOIN scx_ad_group ag ON ag.id=b.ad_group_id
			    LEFT JOIN scx_optimization_goal og ON og.id=ag.optimization_goal_id
			    WHERE ma.media_id=#{mediaId}
			  ),'0')
			),256)
			""")
	String selectSourceHash(@Param("mediaId") String mediaId, @Param("startUtc") LocalDateTime startUtc,
			@Param("endUtc") LocalDateTime endUtc);

	@Select("""
			SELECT NULLIF(GREATEST(
			  COALESCE((
			    SELECT MAX(e.received_at) FROM scx_raw_event e
			    JOIN scx_tracking_task t ON t.id=e.task_id
			    WHERE t.media_id=#{mediaId} AND e.data_origin='LIVE'
			      AND e.occurred_at>=#{startUtc} AND e.occurred_at<#{endUtc}
			  ),'1970-01-01'),
			  COALESCE((
			    SELECT MAX(f.pulled_at) FROM scx_media_report_hourly_fact f
			    WHERE f.media_id=#{mediaId}
			      AND f.stat_hour_utc>=#{startUtc} AND f.stat_hour_utc<#{endUtc}
			  ),'1970-01-01')
			),'1970-01-01')
			""")
	LocalDateTime selectSourceWatermark(@Param("mediaId") String mediaId, @Param("startUtc") LocalDateTime startUtc,
			@Param("endUtc") LocalDateTime endUtc);

	@Insert("""
			INSERT INTO scx_report_hourly
			  (id,report_run_id,stat_hour_utc,report_date,local_hour,timezone,media_id,media_account_id,
			   campaign_id,publishing_binding_id,partner_id,customer_id,product_id,link_id,document_id,task_id,
			   channel_id,media_impression_count,media_click_count,media_conversion_count,unique_click_count,
			   eligible_click_count,deduplicated_click_count,attributed_activate_count,attributed_register_count,
			   attributed_pay_count,attributed_order_count,next_day_return_count,retention_3d_count,
			   retention_7d_count,first_activate_count,first_order_count,reactivate_count,custom_count,credit_count,
			   callback_succeeded_count,callback_failed_count,spend_amount,currency,revision,data_status,
			   quality_code,source_watermark,calculated_at)
			SELECT REPLACE(UUID(),'-',''),#{runId},x.stat_hour_utc,#{date},x.local_hour,#{timezone},
			  x.media_id,MAX(x.media_account_id),MAX(x.campaign_id),MAX(x.publishing_binding_id),
			  MAX(x.partner_id),MAX(x.customer_id),MAX(x.product_id),MAX(x.link_id),MAX(x.document_id),
			  x.task_id,MAX(x.channel_id),
			  SUM(x.media_impression_count),SUM(x.media_click_count),SUM(x.media_conversion_count),
			  SUM(x.unique_click_count),SUM(x.eligible_click_count),SUM(x.deduplicated_click_count),
			  SUM(x.attributed_activate_count),SUM(x.attributed_register_count),SUM(x.attributed_pay_count),
			  SUM(x.attributed_order_count),SUM(x.next_day_return_count),SUM(x.retention_3d_count),
			  SUM(x.retention_7d_count),SUM(x.first_activate_count),SUM(x.first_order_count),
			  SUM(x.reactivate_count),SUM(x.custom_count),SUM(x.credit_count),
			  SUM(x.callback_succeeded_count),SUM(x.callback_failed_count),SUM(x.spend_amount),
			  MAX(x.currency),#{revision},
			  CASE
			    WHEN MAX(x.quality_code) IS NOT NULL THEN 'INCOMPLETE'
			    WHEN SUM(x.media_impression_count+x.media_click_count+x.media_conversion_count)=0 THEN 'INCOMPLETE'
			    WHEN SUM(x.unique_click_count+x.eligible_click_count+x.attributed_activate_count+
			             x.attributed_register_count+x.attributed_pay_count)=0 THEN 'INCOMPLETE'
			    ELSE #{dataStatus}
			  END,
			  CASE
			    WHEN COUNT(DISTINCT x.currency)>1 THEN 'CURRENCY_MISMATCH'
			    WHEN MAX(x.quality_code) IS NOT NULL THEN MAX(x.quality_code)
			    WHEN SUM(x.media_impression_count+x.media_click_count+x.media_conversion_count)=0 THEN 'INTERNAL_ONLY'
			    WHEN SUM(x.unique_click_count+x.eligible_click_count+x.attributed_activate_count+
			             x.attributed_register_count+x.attributed_pay_count)=0 THEN 'MEDIA_ONLY'
			    ELSE NULL
			  END,
			  MAX(x.source_watermark),NOW(3)
			FROM (
			  SELECT
			    STR_TO_DATE(DATE_FORMAT(e.occurred_at,'%Y-%m-%d %H:00:00'),'%Y-%m-%d %H:%i:%s') stat_hour_utc,
			    MOD(TIMESTAMPDIFF(HOUR,#{startUtc},e.occurred_at),24) local_hour,
			    t.media_id,NULL media_account_id,b.campaign_id,b.id publishing_binding_id,
			    l.partner_id,l.customer_id,l.product_id,l.id link_id,l.document_id,e.task_id,t.channel_id,
			    0 media_impression_count,0 media_click_count,0 media_conversion_count,
			    SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END) unique_click_count,
			    SUM(CASE WHEN cd.status='ELIGIBLE' THEN 1 ELSE 0 END) eligible_click_count,
			    SUM(CASE WHEN cd.status='DEDUPLICATED' THEN 1 ELSE 0 END) deduplicated_click_count,
			    SUM(CASE WHEN e.event_type='ACTIVATE' AND a.status='ATTRIBUTED' AND a.current_flag=1
			          AND COALESCE(a.review_status,'PENDING')!='REDUCED'
			          AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END) attributed_activate_count,
			    SUM(CASE WHEN e.event_type='REGISTER' AND a.status='ATTRIBUTED' AND a.current_flag=1
			          AND COALESCE(a.review_status,'PENDING')!='REDUCED'
			          AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END) attributed_register_count,
			    SUM(CASE WHEN e.event_type='PAY' AND a.status='ATTRIBUTED' AND a.current_flag=1
			          AND COALESCE(a.review_status,'PENDING')!='REDUCED'
			          AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END) attributed_pay_count,
			    SUM(CASE WHEN e.event_type='ORDER' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) attributed_order_count,
			    SUM(CASE WHEN e.event_type='NEXT_DAY_RETURN' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) next_day_return_count,
			    SUM(CASE WHEN e.event_type='RETAIN_3D' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) retention_3d_count,
			    SUM(CASE WHEN e.event_type='RETAIN_7D' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) retention_7d_count,
			    SUM(CASE WHEN e.event_type='FIRST_ACTIVATE' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) first_activate_count,
			    SUM(CASE WHEN e.event_type='FIRST_ORDER' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) first_order_count,
			    SUM(CASE WHEN e.event_type='REACTIVATE' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) reactivate_count,
			    SUM(CASE WHEN e.event_type='CUSTOM' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) custom_count,
			    SUM(CASE WHEN e.event_type='CREDIT' AND a.status='ATTRIBUTED' AND a.current_flag=1 THEN 1 ELSE 0 END) credit_count,
			    SUM(CASE WHEN cb.succeeded=1 THEN 1 ELSE 0 END) callback_succeeded_count,
			    SUM(CASE WHEN cb.failed=1 THEN 1 ELSE 0 END) callback_failed_count,
			    0.000000 spend_amount,'CNY' currency,NULL quality_code,MAX(e.received_at) source_watermark
			  FROM scx_raw_event e
			  JOIN scx_tracking_task t ON t.id=e.task_id
			  JOIN scx_tracking_link l ON l.id=t.link_id
			  LEFT JOIN scx_click_dedup_result cd ON cd.click_event_id=e.id
			  LEFT JOIN scx_attribution_result a ON a.conversion_event_id=e.id AND a.current_flag=1
			  LEFT JOIN (
			    SELECT attribution_result_id,
			      MAX(status='SUCCEEDED') succeeded,
			      MAX(status IN ('DEAD','FAILED')) failed
			    FROM scx_callback_job GROUP BY attribution_result_id
			  ) cb ON cb.attribution_result_id=a.id
			  LEFT JOIN scx_media_publishing_binding b ON b.tracking_task_id=t.id
			    AND b.effective_from<=e.occurred_at
			    AND (b.effective_to IS NULL OR b.effective_to>e.occurred_at)
			  WHERE e.occurred_at>=#{startUtc} AND e.occurred_at<#{endUtc}
			    AND e.data_origin='LIVE' AND t.media_id=#{mediaId}
			  GROUP BY stat_hour_utc,local_hour,t.media_id,b.campaign_id,b.id,l.partner_id,l.customer_id,
			    l.product_id,l.id,l.document_id,e.task_id,t.channel_id
			  UNION ALL
			  SELECT f.stat_hour_utc,MOD(TIMESTAMPDIFF(HOUR,#{startUtc},f.stat_hour_utc),24),
			    f.media_id,f.media_account_id,f.campaign_id,
			    f.publishing_binding_id,l.partner_id,l.customer_id,l.product_id,l.id,l.document_id,
			    f.tracking_task_id,t.channel_id,
			    SUM(CASE WHEN f.sync_type='PERFORMANCE' THEN f.impression_count ELSE 0 END),
			    SUM(CASE WHEN f.sync_type='PERFORMANCE' THEN f.click_count ELSE 0 END),
			    SUM(CASE WHEN f.sync_type='PERFORMANCE' THEN f.conversion_count ELSE 0 END),
			    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			    COALESCE(NULLIF(SUM(CASE WHEN f.sync_type='BILLING' THEN f.spend_amount ELSE 0 END),0),
			             SUM(CASE WHEN f.sync_type='PERFORMANCE' THEN f.spend_amount ELSE 0 END)),
			    f.currency,MAX(f.quality_code),MAX(f.pulled_at)
			  FROM scx_media_report_hourly_fact f
			  JOIN scx_tracking_task t ON t.id=f.tracking_task_id
			  JOIN scx_tracking_link l ON l.id=t.link_id
			  WHERE f.stat_hour_utc>=#{startUtc} AND f.stat_hour_utc<#{endUtc}
			    AND f.media_id=#{mediaId}
			    AND f.source_version=(
			      SELECT MAX(f2.source_version)
			      FROM scx_media_report_hourly_fact f2
			      WHERE f2.media_account_id=f.media_account_id
			        AND f2.external_campaign_id=f.external_campaign_id
			        AND f2.stat_hour_utc=f.stat_hour_utc
			        AND f2.sync_type=f.sync_type
			    )
			  GROUP BY f.stat_hour_utc,f.local_hour,f.media_id,f.media_account_id,f.campaign_id,
			    f.publishing_binding_id,l.partner_id,l.customer_id,l.product_id,l.id,l.document_id,
			    f.tracking_task_id,t.channel_id,f.currency
			) x
			GROUP BY x.stat_hour_utc,x.local_hour,x.media_id,x.task_id,x.currency
			""")
	int aggregate(@Param("runId") String runId, @Param("date") LocalDate date, @Param("mediaId") String mediaId,
			@Param("startUtc") LocalDateTime startUtc, @Param("endUtc") LocalDateTime endUtc,
			@Param("revision") int revision, @Param("dataStatus") String dataStatus,
			@Param("timezone") String timezone);

	@Select("""
			<script>
			SELECT CONCAT(CAST(h.report_date AS CHAR),' ',LPAD(h.local_hour,2,'0'),':00') dimension_id,
			  CONCAT(CAST(h.report_date AS CHAR),' ',LPAD(h.local_hour,2,'0'),':00') dimension_name,
			  SUM(h.media_impression_count) impression_count,
			  SUM(h.unique_click_count) unique_click_count,
			  SUM(h.eligible_click_count) eligible_click_count,
			  SUM(h.deduplicated_click_count) deduplicated_click_count,
			  SUM(h.attributed_activate_count) attributed_activate_count,
			  SUM(h.attributed_register_count) attributed_register_count,
			  SUM(h.attributed_pay_count) attributed_pay_count,
			  SUM(h.attributed_order_count) attributed_order_count,
			  SUM(h.next_day_return_count) next_day_return_count,
			  SUM(h.retention_3d_count) retention_3d_count,
			  SUM(h.retention_7d_count) retention_7d_count,
			  SUM(h.first_activate_count) first_activate_count,
			  SUM(h.first_order_count) first_order_count,
			  SUM(h.reactivate_count) reactivate_count,
			  SUM(h.custom_count) custom_count,
			  SUM(h.credit_count) credit_count,
			  SUM(h.media_impression_count) media_impression_count,
			  SUM(h.media_click_count) media_click_count,
			  SUM(h.media_conversion_count) media_conversion_count,
			  SUM(h.callback_succeeded_count) callback_succeeded_count,
			  SUM(h.callback_failed_count) callback_failed_count,
			  SUM(h.media_click_count)-SUM(h.unique_click_count) click_difference_count,
			  CASE WHEN SUM(h.media_click_count)=0 THEN NULL
			    ELSE (SUM(h.media_click_count)-SUM(h.unique_click_count))/SUM(h.media_click_count) END click_difference_rate,
			  SUM(h.spend_amount) spend_amount,MAX(h.currency) currency,
			  MAX(h.data_status) data_status,MAX(h.quality_code) quality_code,
			  CAST(MAX(h.source_watermark) AS CHAR) source_watermark
			FROM scx_report_hourly h
			JOIN scx_report_run r ON r.id=h.report_run_id
			WHERE h.media_id=#{mediaId} AND h.report_date BETWEEN #{startDate} AND #{endDate}
			  AND r.status='SUCCEEDED' AND r.data_origin='LIVE'
			<if test="customerId != null and !customerId.isBlank()">AND h.customer_id=#{customerId}</if>
			<if test="productId != null and !productId.isBlank()">AND h.product_id=#{productId}</if>
			<if test="linkId != null and !linkId.isBlank()">AND h.link_id=#{linkId}</if>
			<if test="taskId != null and !taskId.isBlank()">AND h.task_id=#{taskId}</if>
			<if test="channelId != null and !channelId.isBlank()">AND h.channel_id=#{channelId}</if>
			<if test="documentId != null and !documentId.isBlank()">AND h.document_id=#{documentId}</if>
			<if test="taskRestricted">
			  <choose>
			    <when test="taskIds != null and !taskIds.isEmpty()">AND h.task_id IN
			      <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			    </when>
			    <otherwise>AND 1=0</otherwise>
			  </choose>
			</if>
			  AND r.id=(
			    SELECT r2.id FROM scx_report_run r2
			    WHERE r2.report_date=h.report_date AND r2.media_id=#{mediaId}
			      AND r2.status='SUCCEEDED' AND r2.data_origin='LIVE'
			    ORDER BY r2.finished_at DESC LIMIT 1
			  )
			GROUP BY h.report_date,h.local_hour
			ORDER BY h.report_date DESC,h.local_hour DESC
			</script>
			""")
	List<ReportBreakdown> selectHourly(@Param("mediaId") String mediaId, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("customerId") String customerId,
			@Param("productId") String productId, @Param("linkId") String linkId, @Param("taskId") String taskId,
			@Param("channelId") String channelId, @Param("documentId") String documentId,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

}
