package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.dto.CallbackPayloadContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CallbackJobMapper extends BaseMapper<CallbackJob> {

	@Select("""
			<script>
			SELECT j.*
			FROM scx_callback_job j
			JOIN scx_attribution_result a ON a.id = j.attribution_result_id
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			WHERE t.media_id = #{mediaId}
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			<if test="status != null and status != ''">AND j.status = #{status}</if>
			<if test="rawEventId != null and rawEventId != ''">AND e.id = #{rawEventId}</if>
			ORDER BY j.create_time DESC
			</script>
			""")
	IPage<CallbackJob> selectScopedPage(Page<CallbackJob> page, @Param("mediaId") String mediaId,
			@Param("status") String status, @Param("rawEventId") String rawEventId,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

	@Select("""
			SELECT t.media_id
			FROM scx_callback_job j
			JOIN scx_attribution_result a ON a.id = j.attribution_result_id
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			WHERE j.id = #{id}
			""")
	String selectMediaId(@Param("id") String id);

	@Select("""
			SELECT t.id
			FROM scx_callback_job j
			JOIN scx_attribution_result a ON a.id = j.attribution_result_id
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			WHERE j.id = #{id} AND t.media_id = #{mediaId}
			""")
	String selectTaskId(@Param("id") String id, @Param("mediaId") String mediaId);

	@Select("""
			SELECT t.code AS task_code, l.id AS link_id, l.config_version AS link_config_version,
			       e.event_type AS internal_event, e.occurred_at
			FROM scx_callback_job j
			JOIN scx_attribution_result a ON a.id = j.attribution_result_id
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			JOIN scx_tracking_link l ON l.id = t.link_id
			WHERE j.id = #{id}
			""")
	CallbackPayloadContext selectPayloadContext(@Param("id") String id);

	@Select("""
			<script>
			SELECT j.*
			FROM scx_callback_job j
			JOIN scx_attribution_result a ON a.id = j.attribution_result_id
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			WHERE t.media_id = #{mediaId} AND j.status IN ('PENDING', 'RETRYING')
			  AND (j.next_retry_at IS NULL OR j.next_retry_at &lt;= NOW(3))
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			ORDER BY j.create_time
			LIMIT 200
			</script>
			""")
	List<CallbackJob> selectDispatchableByMedia(@Param("mediaId") String mediaId,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

	@Select("""
			<script>
			SELECT
			  COALESCE(SUM(CASE WHEN j.status='PENDING' THEN 1 ELSE 0 END),0) AS pending_count,
			  COALESCE(SUM(CASE WHEN j.status='RETRYING' THEN 1 ELSE 0 END),0) AS retrying_count,
			  COALESCE(SUM(CASE WHEN j.status='PAUSED' THEN 1 ELSE 0 END),0) AS paused_count,
			  COALESCE(SUM(CASE WHEN j.status='SUCCEEDED' THEN 1 ELSE 0 END),0) AS succeeded_count,
			  COALESCE(SUM(CASE WHEN j.status='DEAD' THEN 1 ELSE 0 END),0) AS dead_count,
			  COALESCE(SUM(CASE WHEN j.next_retry_at IS NOT NULL AND j.next_retry_at > NOW(3) THEN 1 ELSE 0 END),0) AS delayed_count
			FROM scx_callback_job j
			JOIN scx_attribution_result a ON a.id = j.attribution_result_id
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			WHERE t.media_id = #{mediaId}
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			</script>
			""")
	CallbackQueueStats selectStats(@Param("mediaId") String mediaId, @Param("taskRestricted") boolean taskRestricted,
			@Param("taskIds") List<String> taskIds);

	@Select("""
			SELECT
			  COALESCE(SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END),0) AS pending_count,
			  COALESCE(SUM(CASE WHEN status='RETRYING' THEN 1 ELSE 0 END),0) AS retrying_count,
			  COALESCE(SUM(CASE WHEN status='PAUSED' THEN 1 ELSE 0 END),0) AS paused_count,
			  COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN 1 ELSE 0 END),0) AS succeeded_count,
			  COALESCE(SUM(CASE WHEN status='DEAD' THEN 1 ELSE 0 END),0) AS dead_count,
			  COALESCE(SUM(CASE WHEN next_retry_at IS NOT NULL AND next_retry_at > NOW(3) THEN 1 ELSE 0 END),0) AS delayed_count
			FROM scx_callback_job
			""")
	CallbackQueueStats selectGlobalStats();

	@Select("""
			SELECT DISTINCT t.media_id
			FROM scx_callback_job j
			JOIN scx_attribution_result a ON a.id = j.attribution_result_id
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = e.task_id
			WHERE j.status IN ('PENDING', 'RETRYING')
			  AND (j.next_retry_at IS NULL OR j.next_retry_at <= NOW(3))
			LIMIT 100
			""")
	List<String> selectMediaIdsWithDispatchableJobs();

}
