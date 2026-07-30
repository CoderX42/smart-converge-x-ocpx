package com.yzytech.scx.reporting.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.reporting.dto.EventDetailView;
import com.yzytech.scx.reporting.dto.ClickSensitiveDetail;
import com.yzytech.scx.reporting.dto.AttributionResultView;
import com.yzytech.scx.reporting.dto.CallbackResultView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;

@Mapper
public interface EventDetailMapper {

	@Select("""
			<script>
			SELECT a.id attribution_id,e.id raw_event_id,e.event_id,e.event_type,
			  CONVERT_TZ(e.occurred_at,'+00:00','+08:00') occurred_at,e.task_id,t.name task_name,
			  a.status,a.reason_code,a.revision,a.over_limit,a.review_status,a.review_reason,
			  a.click_event_id,CONVERT_TZ(a.attributed_at,'+00:00','+08:00') attributed_at
			FROM scx_attribution_result a
			JOIN scx_raw_event e ON e.id=a.conversion_event_id
			JOIN scx_tracking_task t ON t.id=e.task_id
			WHERE t.media_id=#{mediaId} AND a.current_flag=1
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			<if test="startDate != null">AND e.occurred_at &gt;= DATE_SUB(#{startDate},INTERVAL 8 HOUR)</if>
			<if test="endDate != null">AND e.occurred_at &lt; DATE_SUB(DATE_ADD(#{endDate},INTERVAL 1 DAY),INTERVAL 8 HOUR)</if>
			<if test="eventType != null and eventType != ''">AND e.event_type=#{eventType}</if>
			<if test="status != null and status != ''">AND a.status=#{status}</if>
			<if test="taskId != null and taskId != ''">AND e.task_id=#{taskId}</if>
			ORDER BY e.occurred_at DESC,a.revision DESC
			</script>
			""")
	IPage<AttributionResultView> attributionResults(Page<AttributionResultView> page, @Param("mediaId") String mediaId,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
			@Param("eventType") String eventType, @Param("status") String status, @Param("taskId") String taskId,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") java.util.List<String> taskIds);

	@Select("""
			<script>
			SELECT j.id callback_job_id,a.id attribution_id,e.id raw_event_id,e.event_id,e.event_type,
			  CONVERT_TZ(e.occurred_at,'+00:00','+08:00') occurred_at,e.task_id,t.name task_name,
			  j.target_ref,j.action_type,j.status,j.attempts,j.last_error_code,j.last_error_message,
			  j.next_retry_at,j.create_time,j.update_time,j.completed_at
			FROM scx_callback_job j
			JOIN scx_attribution_result a ON a.id=j.attribution_result_id
			JOIN scx_raw_event e ON e.id=a.conversion_event_id
			JOIN scx_tracking_task t ON t.id=e.task_id
			WHERE t.media_id=#{mediaId}
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			<if test="startDate != null">AND e.occurred_at &gt;= DATE_SUB(#{startDate},INTERVAL 8 HOUR)</if>
			<if test="endDate != null">AND e.occurred_at &lt; DATE_SUB(DATE_ADD(#{endDate},INTERVAL 1 DAY),INTERVAL 8 HOUR)</if>
			<if test="status != null and status != ''">AND j.status=#{status}</if>
			<if test="taskId != null and taskId != ''">AND e.task_id=#{taskId}</if>
			ORDER BY j.create_time DESC
			</script>
			""")
	IPage<CallbackResultView> callbackResults(Page<CallbackResultView> page, @Param("mediaId") String mediaId,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
			@Param("status") String status, @Param("taskId") String taskId,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") java.util.List<String> taskIds);

	@Select("""
			<script>
			SELECT e.id raw_event_id,e.event_id,e.source,e.task_id,t.code task_code,e.event_type,
			  CONVERT_TZ(e.occurred_at, '+00:00', '+08:00') occurred_at,
			  CONVERT_TZ(e.received_at, '+00:00', '+08:00') received_at,
			  CONCAT(LEFT(e.device_key_hash,6),'***',RIGHT(e.device_key_hash,4)) device_key_masked,e.status ingestion_status,
			  COALESCE(cd.status,cv.status) dedup_status,a.status attribution_status,
			  COALESCE(a.reason_code,cd.reason_code,cv.reason_code) reason_code,a.revision
			FROM scx_raw_event e
			JOIN scx_tracking_task t ON t.id=e.task_id
			LEFT JOIN scx_click_dedup_result cd ON cd.click_event_id=e.id
			LEFT JOIN scx_conversion_dedup_result cv ON cv.conversion_event_id=e.id
			LEFT JOIN scx_attribution_result a ON a.conversion_event_id=e.id AND a.current_flag=1
			WHERE t.media_id=#{mediaId}
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			<if test="date != null">
			  AND e.occurred_at &gt;= DATE_SUB(CAST(CONCAT(#{date},' 00:00:00') AS DATETIME),INTERVAL 8 HOUR)
			  AND e.occurred_at &lt; DATE_SUB(CAST(CONCAT(DATE_ADD(#{date},INTERVAL 1 DAY),' 00:00:00') AS DATETIME),INTERVAL 8 HOUR)
			</if>
			<if test="eventType != null and eventType != ''">AND e.event_type=#{eventType}</if>
			<if test="taskId != null and taskId != ''">AND e.task_id=#{taskId}</if>
			<if test="eventId != null and eventId != ''">AND (
			  e.event_id=#{eventId}
			  OR JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.click_id'))=#{eventId}
			  OR JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.clickId'))=#{eventId}
			)</if>
			<if test="dedupStatus != null and dedupStatus != ''">AND COALESCE(cd.status,cv.status)=#{dedupStatus}</if>
			<if test="attributionStatus != null and attributionStatus != ''">AND a.status=#{attributionStatus}</if>
			ORDER BY e.occurred_at DESC
			</script>
			""")
	IPage<EventDetailView> page(Page<EventDetailView> page, @Param("mediaId") String mediaId,
			@Param("date") LocalDate date, @Param("eventType") String eventType, @Param("taskId") String taskId,
			@Param("eventId") String eventId, @Param("dedupStatus") String dedupStatus,
			@Param("attributionStatus") String attributionStatus, @Param("taskRestricted") boolean taskRestricted,
			@Param("taskIds") java.util.List<String> taskIds);

	@Select("""
			SELECT e.task_id FROM scx_raw_event e
			JOIN scx_tracking_task t ON t.id=e.task_id
			WHERE e.id=#{rawEventId} AND t.media_id=#{mediaId}
			""")
	String selectTaskId(@Param("rawEventId") String rawEventId, @Param("mediaId") String mediaId);

	@Select("""
			SELECT e.id AS raw_event_id,
			  CONCAT(LEFT(NULLIF(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.click_id')),JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.clickId'))),''),6),'***') AS click_id_masked,
			  JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.device_model')) AS device_model,
			  CONCAT(LEFT(NULLIF(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.campaign_id')),JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.campaignId'))),''),6),'***') AS campaign_id_masked,
			  CONCAT(LEFT(NULLIF(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.creative_id')),JSON_UNQUOTE(JSON_EXTRACT(e.payload_json,'$.attributes.creativeId'))),''),6),'***') AS creative_id_masked,
			  JSON_CONTAINS_PATH(e.payload_json, 'one', '$.attributes.oaid') AS oaid_protected,
			  JSON_CONTAINS_PATH(e.payload_json, 'one', '$.attributes.oaid_md5', '$.attributes.oaidMd5') AS oaid_md5_protected,
			  JSON_CONTAINS_PATH(e.payload_json, 'one', '$.attributes.ip') AS ip_protected,
			  JSON_KEYS(JSON_EXTRACT(e.payload_json, '$.attributes')) AS attribute_keys_json
			FROM scx_raw_event e
			JOIN scx_tracking_task t ON t.id=e.task_id
			WHERE e.id=#{rawEventId} AND e.event_type='CLICK' AND t.media_id=#{mediaId}
			""")
	ClickSensitiveDetail selectMaskedClick(@Param("rawEventId") String rawEventId, @Param("mediaId") String mediaId);

}
