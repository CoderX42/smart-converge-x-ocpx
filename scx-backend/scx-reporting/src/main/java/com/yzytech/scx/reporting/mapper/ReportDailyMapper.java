package com.yzytech.scx.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.reporting.dto.ReportBreakdown;
import com.yzytech.scx.reporting.dto.ReportTrendPoint;
import com.yzytech.scx.reporting.entity.ReportDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReportDailyMapper extends BaseMapper<ReportDaily> {

	@Insert("""
			INSERT INTO scx_report_daily
			  (id,report_run_id,report_date,timezone,media_id,media_account_id,campaign_id,publishing_binding_id,
			   partner_id,customer_id,product_id,link_id,document_id,task_id,channel_id,
			   impression_count,unique_click_count,eligible_click_count,deduplicated_click_count,
			   attributed_activate_count,attributed_register_count,attributed_pay_count,attributed_order_count,
			   next_day_return_count,retention_3d_count,retention_7d_count,first_activate_count,first_order_count,
			   reactivate_count,custom_count,credit_count,media_impression_count,media_click_count,
			   media_conversion_count,spend_amount,currency,callback_succeeded_count,callback_failed_count,
			   data_status,quality_code,source_watermark,data_origin,revision,calculated_at)
			SELECT REPLACE(UUID(),'-',''),#{runId},#{date},#{timezone},h.media_id,
			  MAX(h.media_account_id),MAX(h.campaign_id),MAX(h.publishing_binding_id),MAX(h.partner_id),
			  MAX(h.customer_id),MAX(h.product_id),MAX(h.link_id),MAX(h.document_id),h.task_id,MAX(h.channel_id),
			  SUM(h.media_impression_count),SUM(h.unique_click_count),SUM(h.eligible_click_count),
			  SUM(h.deduplicated_click_count),SUM(h.attributed_activate_count),SUM(h.attributed_register_count),
			  SUM(h.attributed_pay_count),SUM(h.attributed_order_count),SUM(h.next_day_return_count),
			  SUM(h.retention_3d_count),SUM(h.retention_7d_count),SUM(h.first_activate_count),
			  SUM(h.first_order_count),SUM(h.reactivate_count),SUM(h.custom_count),SUM(h.credit_count),
			  SUM(h.media_impression_count),SUM(h.media_click_count),SUM(h.media_conversion_count),
			  SUM(h.spend_amount),MAX(h.currency),SUM(h.callback_succeeded_count),SUM(h.callback_failed_count),
			  CASE WHEN SUM(h.data_status='INCOMPLETE')>0 THEN 'INCOMPLETE' ELSE #{dataStatus} END,
			  CASE WHEN COUNT(DISTINCT h.currency)>1 THEN 'CURRENCY_MISMATCH'
			       WHEN MAX(h.quality_code) IS NOT NULL THEN MAX(h.quality_code) ELSE NULL END,
			  MAX(h.source_watermark),'LIVE',#{revision},NOW(3)
			FROM scx_report_hourly h
			WHERE h.report_run_id=#{runId} AND h.media_id=#{mediaId} AND h.report_date=#{date}
			GROUP BY h.media_id,h.task_id,h.currency
			""")
	int aggregateFromHourly(@Param("runId") String runId, @Param("date") LocalDate date,
			@Param("mediaId") String mediaId, @Param("revision") int revision, @Param("dataStatus") String dataStatus,
			@Param("timezone") String timezone);

	@org.apache.ibatis.annotations.Select("""
			SELECT CASE WHEN SUM(data_status='INCOMPLETE')>0 THEN 'INCOMPLETE'
			  ELSE MAX(data_status) END
			FROM scx_report_daily
			WHERE report_run_id=#{runId}
			""")
	String selectRunDataStatus(@Param("runId") String runId);

	@org.apache.ibatis.annotations.Select("""
			<script>
			SELECT d.*
			FROM scx_report_daily d
			JOIN scx_report_run r ON r.id=d.report_run_id
			WHERE d.media_id=#{mediaId}
			  AND d.report_date BETWEEN #{startDate} AND #{endDate}
			  AND d.data_origin='LIVE'
			  <if test="taskRestricted">
			    <choose>
			      <when test="taskIds != null and !taskIds.isEmpty()">
			        AND d.task_id IN
			        <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			      </when>
			      <otherwise>AND 1=0</otherwise>
			    </choose>
			  </if>
			  AND r.status='SUCCEEDED'
			  AND r.id=(
			    SELECT r2.id FROM scx_report_run r2
			    WHERE r2.report_date=d.report_date AND r2.media_id=#{mediaId}
			      AND r2.status='SUCCEEDED' AND r2.data_origin='LIVE'
			      AND EXISTS (SELECT 1 FROM scx_report_daily d2 WHERE d2.report_run_id=r2.id AND d2.media_id=#{mediaId})
			    ORDER BY r2.finished_at DESC LIMIT 1
			  )
			ORDER BY d.report_date,d.task_id
			LIMIT #{limit}
			</script>
			""")
	List<ReportDaily> selectForExport(@Param("mediaId") String mediaId, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("limit") int limit,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

	@org.apache.ibatis.annotations.Select("""
			<script>
			SELECT
			<choose>
			  <when test="dimension == 'DATE'">CAST(d.report_date AS CHAR) AS dimension_id, CAST(d.report_date AS CHAR) AS dimension_name</when>
			  <when test="dimension == 'HOURLY'">CONCAT(CAST(d.report_date AS CHAR), ' 00:00') AS dimension_id, CAST(d.report_date AS CHAR) AS dimension_name</when>
			  <when test="dimension == 'CPA'">CONCAT(d.product_id, '|', d.task_id, '|', d.customer_id, '|', d.currency) AS dimension_id, CONCAT(MAX(COALESCE(pr.name, d.product_id)), ' / ', MAX(COALESCE(t.name, d.task_id)), ' / ', MAX(COALESCE(c.name, d.customer_id)), ' / ', d.currency) AS dimension_name</when>
			  <when test="dimension == 'RESULT'">CONCAT(d.task_id, '|', 'RESULT') AS dimension_id, CONCAT(MAX(COALESCE(t.name, d.task_id)), ' / ', 'RESULT') AS dimension_name</when>
			  <when test="dimension == 'RESULT_CB'">CONCAT(d.task_id, '|', 'RESULT_CB') AS dimension_id, CONCAT(MAX(COALESCE(t.name, d.task_id)), ' / ', 'RESULT_CB') AS dimension_name</when>
			  <when test="dimension == 'MEDIA'">d.media_id AS dimension_id, MAX(COALESCE(m.name, d.media_id)) AS dimension_name</when>
			  <when test="dimension == 'MEDIA_ACCOUNT'">COALESCE(d.media_account_id,'INTERNAL_ONLY') AS dimension_id, MAX(COALESCE(ma.account_name,'仅内部追踪事实')) AS dimension_name</when>
			  <when test="dimension == 'PARTNER'">d.partner_id AS dimension_id, MAX(COALESCE(p.name, d.partner_id)) AS dimension_name</when>
			  <when test="dimension == 'CUSTOMER'">d.customer_id AS dimension_id, MAX(COALESCE(c.name, d.customer_id)) AS dimension_name</when>
			  <when test="dimension == 'PRODUCT'">d.product_id AS dimension_id, MAX(COALESCE(pr.name, d.product_id)) AS dimension_name</when>
			  <when test="dimension == 'LINK'">d.link_id AS dimension_id, MAX(COALESCE(l.name, d.link_id)) AS dimension_name</when>
			  <when test="dimension == 'DOCUMENT'">d.document_id AS dimension_id, MAX(COALESCE(doc.name, d.document_id)) AS dimension_name</when>
			  <when test="dimension == 'CHANNEL'">d.channel_id AS dimension_id, MAX(COALESCE(ch.name, d.channel_id)) AS dimension_name</when>
			  <otherwise>d.task_id AS dimension_id, MAX(COALESCE(t.name, d.task_id)) AS dimension_name</otherwise>
			</choose>,
			SUM(d.impression_count) AS impression_count,
			SUM(d.unique_click_count) AS unique_click_count,
			SUM(d.eligible_click_count) AS eligible_click_count,
			SUM(d.deduplicated_click_count) AS deduplicated_click_count,
			SUM(d.attributed_activate_count) AS attributed_activate_count,
			SUM(d.attributed_register_count) AS attributed_register_count,
			SUM(d.attributed_pay_count) AS attributed_pay_count
			,SUM(d.attributed_order_count) AS attributed_order_count
			,SUM(d.next_day_return_count) AS next_day_return_count
			,SUM(d.retention_3d_count) AS retention_3d_count
			,SUM(d.retention_7d_count) AS retention_7d_count
			,SUM(d.first_activate_count) AS first_activate_count
			,SUM(d.first_order_count) AS first_order_count
			,SUM(d.reactivate_count) AS reactivate_count
			,SUM(d.custom_count) AS custom_count
			,SUM(d.credit_count) AS credit_count
			,SUM(d.media_impression_count) AS media_impression_count
			,SUM(d.media_click_count) AS media_click_count
			,SUM(d.media_conversion_count) AS media_conversion_count
			,SUM(d.callback_succeeded_count) AS callback_succeeded_count
			,SUM(d.callback_failed_count) AS callback_failed_count
			,SUM(d.media_click_count)-SUM(d.unique_click_count) AS click_difference_count
			,CASE WHEN SUM(d.media_click_count)=0 THEN NULL
			      ELSE (SUM(d.media_click_count)-SUM(d.unique_click_count))/SUM(d.media_click_count) END AS click_difference_rate
			,SUM(d.spend_amount) AS spend_amount
			,MAX(d.currency) AS currency
			,SUM(CASE
			  WHEN og.event_code='REGISTER' THEN d.attributed_register_count
			  WHEN og.event_code='PAY' THEN d.attributed_pay_count
			  WHEN og.event_code='CLICK' THEN d.eligible_click_count
			  ELSE d.attributed_activate_count END) AS attributed_target_count
			,CASE
			  WHEN MAX(d.quality_code) IS NOT NULL THEN NULL
			  WHEN COUNT(DISTINCT d.currency)>1 THEN NULL
			  WHEN SUM(CASE
			    WHEN og.event_code='REGISTER' THEN d.attributed_register_count
			    WHEN og.event_code='PAY' THEN d.attributed_pay_count
			    WHEN og.event_code='CLICK' THEN d.eligible_click_count
			    ELSE d.attributed_activate_count END)=0 THEN NULL
			  ELSE SUM(d.spend_amount)/SUM(CASE
			    WHEN og.event_code='REGISTER' THEN d.attributed_register_count
			    WHEN og.event_code='PAY' THEN d.attributed_pay_count
			    WHEN og.event_code='CLICK' THEN d.eligible_click_count
			    ELSE d.attributed_activate_count END)
			  END AS cpa
			,CASE WHEN SUM(d.data_status='INCOMPLETE')>0 THEN 'INCOMPLETE'
			      WHEN SUM(d.data_status='PRELIMINARY')>0 THEN 'PRELIMINARY'
			      ELSE MAX(d.data_status) END AS data_status
			,MAX(d.quality_code) AS quality_code
			,CAST(MAX(d.source_watermark) AS CHAR) AS source_watermark
			FROM scx_report_daily d
			JOIN scx_report_run r ON r.id = d.report_run_id
			LEFT JOIN scx_partner p ON p.id = d.partner_id
			LEFT JOIN scx_media m ON m.id = d.media_id
			LEFT JOIN scx_media_account ma ON ma.id = d.media_account_id
			LEFT JOIN scx_upstream_customer c ON c.id = d.customer_id
			LEFT JOIN scx_product pr ON pr.id = d.product_id
			LEFT JOIN scx_tracking_link l ON l.id = d.link_id
			LEFT JOIN scx_integration_document doc ON doc.id = d.document_id
			LEFT JOIN scx_tracking_task t ON t.id = d.task_id
			LEFT JOIN scx_media_channel ch ON ch.id = d.channel_id
			LEFT JOIN scx_media_publishing_binding mpb ON mpb.id = d.publishing_binding_id
			LEFT JOIN scx_ad_group ag ON ag.id = mpb.ad_group_id
			LEFT JOIN scx_optimization_goal og ON og.id = ag.optimization_goal_id AND og.status = 'ACTIVE'
			WHERE d.media_id = #{mediaId}
			  AND d.report_date BETWEEN #{startDate} AND #{endDate}
			  AND d.data_origin = 'LIVE'
			  <if test="customerId != null and !customerId.isBlank()">AND d.customer_id = #{customerId}</if>
			  <if test="productId != null and !productId.isBlank()">AND d.product_id = #{productId}</if>
			  <if test="linkId != null and !linkId.isBlank()">AND d.link_id = #{linkId}</if>
			  <if test="taskId != null and !taskId.isBlank()">AND d.task_id = #{taskId}</if>
			  <if test="channelId != null and !channelId.isBlank()">AND d.channel_id = #{channelId}</if>
			  <if test="documentId != null and !documentId.isBlank()">AND d.document_id = #{documentId}</if>
			  <if test="taskRestricted">
			    <choose>
			      <when test="taskIds != null and !taskIds.isEmpty()">
			        AND d.task_id IN
			        <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			      </when>
			      <otherwise>AND 1=0</otherwise>
			    </choose>
			  </if>
			  AND r.status = 'SUCCEEDED'
			  AND r.id = (
			    SELECT r2.id FROM scx_report_run r2
			    WHERE r2.report_date = d.report_date AND r2.media_id=#{mediaId}
			      AND r2.status = 'SUCCEEDED' AND r2.data_origin='LIVE'
			      AND EXISTS (SELECT 1 FROM scx_report_daily d2 WHERE d2.report_run_id = r2.id AND d2.media_id = #{mediaId})
			    ORDER BY r2.finished_at DESC LIMIT 1
			  )
			GROUP BY
			<choose>
			  <when test="dimension == 'DATE'">d.report_date</when>
			  <when test="dimension == 'HOURLY'">d.report_date</when>
			  <when test="dimension == 'CPA'">d.product_id, d.task_id, d.customer_id, d.currency</when>
			  <when test="dimension == 'RESULT'">d.task_id</when>
			  <when test="dimension == 'RESULT_CB'">d.task_id</when>
			  <when test="dimension == 'MEDIA'">d.media_id</when>
			  <when test="dimension == 'MEDIA_ACCOUNT'">COALESCE(d.media_account_id,'INTERNAL_ONLY')</when>
			  <when test="dimension == 'PARTNER'">d.partner_id</when>
			  <when test="dimension == 'CUSTOMER'">d.customer_id</when>
			  <when test="dimension == 'PRODUCT'">d.product_id</when>
			  <when test="dimension == 'LINK'">d.link_id</when>
			  <when test="dimension == 'DOCUMENT'">d.document_id</when>
			  <when test="dimension == 'CHANNEL'">d.channel_id</when>
			  <otherwise>d.task_id</otherwise>
			</choose>
			ORDER BY
			<choose>
			  <when test="dimension == 'DATE'">d.report_date DESC</when>
			  <when test="dimension == 'HOURLY'">dimension_id DESC</when>
			  <when test="dimension == 'CPA'">d.product_id, d.task_id, d.customer_id, d.currency</when>
			  <when test="dimension == 'RESULT'">d.task_id, dimension_id</when>
			  <when test="dimension == 'RESULT_CB'">d.task_id, dimension_id</when>
			  <otherwise>unique_click_count DESC</otherwise>
			</choose>
			</script>
			""")
	List<ReportBreakdown> selectBreakdown(@Param("mediaId") String mediaId, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("dimension") String dimension,
			@Param("customerId") String customerId, @Param("productId") String productId,
			@Param("linkId") String linkId, @Param("taskId") String taskId, @Param("channelId") String channelId,
			@Param("documentId") String documentId, @Param("taskRestricted") boolean taskRestricted,
			@Param("taskIds") List<String> taskIds);

	@org.apache.ibatis.annotations.Select("""
			<script>
			SELECT d.report_date,
			  SUM(d.impression_count) AS impression_count,
			  SUM(d.unique_click_count) AS unique_click_count,
			  SUM(d.eligible_click_count) AS eligible_click_count,
			  SUM(d.attributed_activate_count) AS attributed_activate_count,
			  SUM(d.attributed_register_count) AS attributed_register_count,
			  SUM(d.attributed_pay_count) AS attributed_pay_count,
			  SUM(d.attributed_order_count) AS attributed_order_count,
			  SUM(d.next_day_return_count) AS next_day_return_count
			FROM scx_report_daily d
			JOIN scx_report_run r ON r.id=d.report_run_id
			WHERE d.media_id=#{mediaId}
			  AND d.report_date BETWEEN #{startDate} AND #{endDate}
			  AND d.data_origin='LIVE'
			  <if test="taskRestricted">
			    <choose>
			      <when test="taskIds != null and !taskIds.isEmpty()">
			        AND d.task_id IN
			        <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			      </when>
			      <otherwise>AND 1=0</otherwise>
			    </choose>
			  </if>
			  AND r.status='SUCCEEDED'
			  AND r.id=(
			    SELECT r2.id FROM scx_report_run r2
			    WHERE r2.report_date=d.report_date AND r2.media_id=#{mediaId}
			      AND r2.status='SUCCEEDED' AND r2.data_origin='LIVE'
			      AND EXISTS (SELECT 1 FROM scx_report_daily d2 WHERE d2.report_run_id=r2.id AND d2.media_id=#{mediaId})
			    ORDER BY r2.finished_at DESC LIMIT 1
			  )
			GROUP BY d.report_date
			ORDER BY d.report_date
			</script>
			""")
	List<ReportTrendPoint> selectTrend(@Param("mediaId") String mediaId, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("taskRestricted") boolean taskRestricted,
			@Param("taskIds") List<String> taskIds);

}
