package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.tracking.dto.AttributionReviewView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AttributionReviewMapper {

	@Select("""
			<script>
			SELECT a.id, t.media_id, t.id AS task_id, t.code AS task_code, t.name AS task_name,
			       a.conversion_event_id, conversion_event.event_type AS conversion_event_type,
			       conversion_event.occurred_at AS conversion_occurred_at, a.click_event_id,
			       click_event.occurred_at AS click_occurred_at, a.status AS attribution_status,
			       a.reason_code AS attribution_reason, a.over_limit, a.review_status, a.review_reason,
			       a.reviewed_by, a.reviewed_at, a.attributed_at,
			       (SELECT j.action_type FROM scx_callback_job j WHERE j.attribution_result_id = a.id
			            ORDER BY j.create_time DESC LIMIT 1) AS callback_action_type,
			       (SELECT j.status FROM scx_callback_job j WHERE j.attribution_result_id = a.id
			            ORDER BY j.create_time DESC LIMIT 1) AS callback_status,
			       (SELECT j.attempts FROM scx_callback_job j WHERE j.attribution_result_id = a.id
			            ORDER BY j.create_time DESC LIMIT 1) AS callback_attempts,
			       (SELECT j.last_error_code FROM scx_callback_job j WHERE j.attribution_result_id = a.id
			            ORDER BY j.create_time DESC LIMIT 1) AS callback_last_error_code,
			       (SELECT j.last_error_message FROM scx_callback_job j WHERE j.attribution_result_id = a.id
			            ORDER BY j.create_time DESC LIMIT 1) AS callback_last_error_message,
			       (SELECT j.update_time FROM scx_callback_job j WHERE j.attribution_result_id = a.id
			            ORDER BY j.create_time DESC LIMIT 1) AS callback_updated_at
			FROM scx_attribution_result a
			JOIN scx_raw_event conversion_event ON conversion_event.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = conversion_event.task_id
			LEFT JOIN scx_raw_event click_event ON click_event.id = a.click_event_id
			WHERE t.media_id = #{mediaId} AND a.current_flag = 1
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			<if test="reviewStatus != null and reviewStatus != ''">AND a.review_status = #{reviewStatus}</if>
			<if test="overLimit != null">AND a.over_limit = #{overLimit}</if>
			<if test="taskId != null and taskId != ''">AND t.id = #{taskId}</if>
			<if test="startDate != null">AND DATE(conversion_event.occurred_at) &gt;= #{startDate}</if>
			<if test="endDate != null">AND DATE(conversion_event.occurred_at) &lt;= #{endDate}</if>
			<if test="callbackStatus != null and callbackStatus != ''">AND EXISTS (
			  SELECT 1 FROM scx_callback_job callback_filter
			  WHERE callback_filter.attribution_result_id = a.id AND callback_filter.status = #{callbackStatus}
			)</if>
			ORDER BY a.attributed_at DESC
			</script>
			""")
	IPage<AttributionReviewView> selectScopedPage(Page<AttributionReviewView> page, @Param("mediaId") String mediaId,
			@Param("reviewStatus") String reviewStatus, @Param("overLimit") Boolean overLimit,
			@Param("taskId") String taskId, @Param("startDate") java.time.LocalDate startDate,
			@Param("endDate") java.time.LocalDate endDate, @Param("callbackStatus") String callbackStatus,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") java.util.List<String> taskIds);

	@Select("""
			<script>
			SELECT a.id, t.media_id, t.id AS task_id, t.code AS task_code, t.name AS task_name,
			       a.conversion_event_id, conversion_event.event_type AS conversion_event_type,
			       conversion_event.occurred_at AS conversion_occurred_at, a.click_event_id,
			       click_event.occurred_at AS click_occurred_at, a.status AS attribution_status,
			       a.reason_code AS attribution_reason, a.over_limit, a.review_status, a.review_reason,
			       a.reviewed_by, a.reviewed_at, a.attributed_at
			FROM scx_attribution_result a
			JOIN scx_raw_event conversion_event ON conversion_event.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = conversion_event.task_id
			LEFT JOIN scx_raw_event click_event ON click_event.id = a.click_event_id
			WHERE t.media_id = #{mediaId}
			  AND a.current_flag = 1
			  AND DATE(conversion_event.occurred_at) BETWEEN #{startDate} AND #{endDate}
			  AND (a.review_status = 'REDUCED' OR COALESCE(a.over_limit, 0) = 1)
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			ORDER BY conversion_event.occurred_at DESC, a.id
			LIMIT #{limit}
			</script>
			""")
	java.util.List<AttributionReviewView> selectReductionForExport(@Param("mediaId") String mediaId,
			@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate,
			@Param("limit") int limit, @Param("taskRestricted") boolean taskRestricted,
			@Param("taskIds") java.util.List<String> taskIds);

	@Select("""
			SELECT t.media_id
			FROM scx_attribution_result a
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			WHERE a.id = #{attributionId}
			""")
	String selectMediaId(@Param("attributionId") String attributionId);

	@Select("""
			SELECT e.task_id FROM scx_attribution_result a
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			WHERE a.id = #{attributionId} AND t.media_id = #{mediaId}
			""")
	String selectTaskId(@Param("attributionId") String attributionId, @Param("mediaId") String mediaId);

}
