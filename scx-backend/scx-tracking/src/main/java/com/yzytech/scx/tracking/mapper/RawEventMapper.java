package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.tracking.entity.RawEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;

@Mapper
public interface RawEventMapper extends BaseMapper<RawEvent> {

	@Select("""
			SELECT e.* FROM scx_raw_event e
			JOIN scx_click_dedup_result d ON d.click_event_id=e.id AND d.status='ELIGIBLE'
			WHERE e.task_id=#{taskId} AND e.event_type='CLICK' AND e.device_key_hash=#{deviceKeyHash}
			  AND e.occurred_at BETWEEN #{from} AND #{to}
			ORDER BY e.occurred_at DESC LIMIT 1
			""")
	RawEvent findLastEligibleClick(@Param("taskId") String taskId, @Param("deviceKeyHash") String deviceKeyHash,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}
