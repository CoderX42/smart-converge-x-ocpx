package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.dto.OutboxQueueStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {

	@Select("""
			<script>
			SELECT o.*
			FROM scx_event_outbox o
			LEFT JOIN scx_raw_event direct_event ON direct_event.id = o.aggregate_id
			LEFT JOIN scx_attribution_result a ON a.id = o.aggregate_id
			LEFT JOIN scx_raw_event conversion_event ON conversion_event.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = COALESCE(direct_event.task_id, conversion_event.task_id)
			WHERE t.media_id = #{mediaId}
			<if test="status != null and status != ''">AND o.status = #{status}</if>
			<if test="aggregateId != null and aggregateId != ''">AND o.aggregate_id = #{aggregateId}</if>
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			ORDER BY o.create_time DESC
			</script>
			""")
	IPage<EventOutbox> selectScopedPage(Page<EventOutbox> page, @Param("mediaId") String mediaId,
			@Param("status") String status, @Param("aggregateId") String aggregateId,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

	@Select("""
			<script>
			SELECT o.*
			FROM scx_event_outbox o
			LEFT JOIN scx_raw_event direct_event ON direct_event.id = o.aggregate_id
			LEFT JOIN scx_attribution_result a ON a.id = o.aggregate_id
			LEFT JOIN scx_raw_event conversion_event ON conversion_event.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = COALESCE(direct_event.task_id, conversion_event.task_id)
			WHERE o.id = #{id} AND t.media_id = #{mediaId}
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			LIMIT 1
			</script>
			""")
	EventOutbox selectScopedById(@Param("id") String id, @Param("mediaId") String mediaId,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

	@Select("""
			<script>
			SELECT o.*
			FROM scx_event_outbox o
			LEFT JOIN scx_raw_event direct_event ON direct_event.id = o.aggregate_id
			LEFT JOIN scx_attribution_result a ON a.id = o.aggregate_id
			LEFT JOIN scx_raw_event conversion_event ON conversion_event.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = COALESCE(direct_event.task_id, conversion_event.task_id)
			WHERE t.media_id = #{mediaId}
			  AND o.status IN ('PENDING', 'RETRYING')
			  AND (o.next_retry_at IS NULL OR o.next_retry_at &lt;= NOW(3))
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			ORDER BY o.create_time
			LIMIT 200
			</script>
			""")
	List<EventOutbox> selectPendingByMedia(@Param("mediaId") String mediaId,
			@Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

	@Select("""
			SELECT DISTINCT t.media_id
			FROM scx_event_outbox o
			LEFT JOIN scx_raw_event direct_event ON direct_event.id = o.aggregate_id
			LEFT JOIN scx_attribution_result a ON a.id = o.aggregate_id
			LEFT JOIN scx_raw_event conversion_event ON conversion_event.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = COALESCE(direct_event.task_id, conversion_event.task_id)
			WHERE o.status IN ('PENDING', 'RETRYING')
			  AND (o.next_retry_at IS NULL OR o.next_retry_at <= NOW(3))
			""")
	List<String> selectMediaIdsWithDispatchableOutbox();

	@Select("""
			<script>
			SELECT
			  COALESCE(SUM(CASE WHEN o.status='PENDING' THEN 1 ELSE 0 END),0) AS pending_count,
			  COALESCE(SUM(CASE WHEN o.status='RETRYING' THEN 1 ELSE 0 END),0) AS retrying_count,
			  COALESCE(SUM(CASE WHEN o.status='SENT' THEN 1 ELSE 0 END),0) AS sent_count,
			  COALESCE(SUM(CASE WHEN o.status='DEAD' THEN 1 ELSE 0 END),0) AS dead_count,
			  COALESCE(SUM(CASE WHEN o.next_retry_at IS NOT NULL AND o.next_retry_at > NOW(3) THEN 1 ELSE 0 END),0) AS delayed_count
			FROM scx_event_outbox o
			LEFT JOIN scx_raw_event direct_event ON direct_event.id = o.aggregate_id
			LEFT JOIN scx_attribution_result a ON a.id = o.aggregate_id
			LEFT JOIN scx_raw_event conversion_event ON conversion_event.id = a.conversion_event_id
			JOIN scx_tracking_task t ON t.id = COALESCE(direct_event.task_id, conversion_event.task_id)
			WHERE t.media_id = #{mediaId}
			<if test="taskRestricted and (taskIds == null or taskIds.size == 0)">AND 1=0</if>
			<if test="taskRestricted and taskIds != null and taskIds.size > 0">AND t.id IN
			  <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
			</if>
			</script>
			""")
	OutboxQueueStats selectStats(@Param("mediaId") String mediaId, @Param("taskRestricted") boolean taskRestricted,
			@Param("taskIds") List<String> taskIds);

	@Select("""
			SELECT
			  COALESCE(SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END),0) AS pending_count,
			  COALESCE(SUM(CASE WHEN status='RETRYING' THEN 1 ELSE 0 END),0) AS retrying_count,
			  COALESCE(SUM(CASE WHEN status='SENT' THEN 1 ELSE 0 END),0) AS sent_count,
			  COALESCE(SUM(CASE WHEN status='DEAD' THEN 1 ELSE 0 END),0) AS dead_count,
			  COALESCE(SUM(CASE WHEN next_retry_at IS NOT NULL AND next_retry_at > NOW(3) THEN 1 ELSE 0 END),0) AS delayed_count
			FROM scx_event_outbox
			""")
	OutboxQueueStats selectGlobalStats();

	@Select("""
			SELECT id FROM scx_event_outbox FORCE INDEX (idx_scx_outbox_lease_claim)
			WHERE status IN ('PENDING','RETRYING')
			  AND (next_retry_at IS NULL OR next_retry_at <= NOW(3))
			  AND (locked_until IS NULL OR locked_until < NOW(3))
			ORDER BY locked_until, status, next_retry_at, create_time, id LIMIT #{limit} FOR UPDATE SKIP LOCKED
			""")
	List<String> selectClaimableIdsForUpdate(@Param("limit") int limit);

	@Update("""
			<script>
			UPDATE scx_event_outbox SET locked_by=#{owner}, locked_until=#{lockedUntil}
			WHERE id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
			</script>
			""")
	int claim(@Param("ids") List<String> ids, @Param("owner") String owner,
			@Param("lockedUntil") java.time.LocalDateTime lockedUntil);

	@Select("""
			<script>
			SELECT * FROM scx_event_outbox WHERE locked_by=#{owner}
			AND id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
			ORDER BY create_time
			</script>
			""")
	List<EventOutbox> selectOwned(@Param("ids") List<String> ids, @Param("owner") String owner);

	@Update("""
			UPDATE scx_event_outbox SET status=#{value.status}, attempts=#{value.attempts},
			next_retry_at=#{value.nextRetryAt}, sent_at=#{value.sentAt},
			last_error_code=#{value.lastErrorCode}, last_error_message=#{value.lastErrorMessage},
			locked_by=NULL, locked_until=NULL
			WHERE id=#{value.id} AND locked_by=#{owner}
			""")
	int completeOwned(@Param("value") EventOutbox value, @Param("owner") String owner);

}
