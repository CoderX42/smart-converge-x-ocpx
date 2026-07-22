package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.tracking.entity.RawEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RawEventMapper extends BaseMapper<RawEvent> {

	@Select("SELECT id FROM scx_tracking_task WHERE code=#{taskCode} AND status='ACTIVE' LIMIT 1")
	String selectTaskIdByCode(@Param("taskCode") String taskCode);

	@Select("SELECT * FROM scx_raw_event WHERE id=#{id} FOR UPDATE")
	RawEvent selectByIdForUpdate(@Param("id") String id);

	@Select("""
			SELECT * FROM scx_raw_event
			WHERE processing_status='PENDING'
			ORDER BY received_at, id
			LIMIT #{limit}
			""")
	List<RawEvent> selectPending(@Param("limit") int limit);

	@Select("""
			SELECT e.* FROM scx_raw_event e
			JOIN scx_click_dedup_result d ON d.click_event_id=e.id AND d.status='ELIGIBLE'
			WHERE e.task_id=#{taskId} AND e.event_type='CLICK' AND e.device_key_hash=#{deviceKeyHash}
			  AND e.occurred_at BETWEEN #{from} AND #{to}
			ORDER BY e.occurred_at DESC LIMIT 1
			""")
	RawEvent findLastEligibleClick(@Param("taskId") String taskId, @Param("deviceKeyHash") String deviceKeyHash,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Select("""
			SELECT id FROM scx_raw_event FORCE INDEX (idx_scx_raw_lease_claim)
			WHERE processing_status='PENDING' AND (locked_until IS NULL OR locked_until < NOW(3))
			ORDER BY locked_until, received_at, id LIMIT #{limit} FOR UPDATE SKIP LOCKED
			""")
	List<String> selectClaimableIdsForUpdate(@Param("limit") int limit);

	@Update("""
			<script>
			UPDATE scx_raw_event SET locked_by=#{owner}, locked_until=#{lockedUntil}
			WHERE id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
			</script>
			""")
	int claim(@Param("ids") List<String> ids, @Param("owner") String owner,
			@Param("lockedUntil") LocalDateTime lockedUntil);

	@Select("""
			<script>
			SELECT * FROM scx_raw_event WHERE locked_by=#{owner}
			AND id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
			ORDER BY received_at, id
			</script>
			""")
	List<RawEvent> selectOwned(@Param("ids") List<String> ids, @Param("owner") String owner);

}
