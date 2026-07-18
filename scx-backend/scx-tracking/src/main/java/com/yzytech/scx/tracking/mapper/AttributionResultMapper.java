package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.tracking.entity.AttributionResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;

@Mapper
public interface AttributionResultMapper extends BaseMapper<AttributionResult> {

	@Select("""
			SELECT COUNT(*)
			FROM scx_attribution_result a
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			WHERE e.task_id = #{taskId} AND DATE(e.occurred_at) = #{date}
			  AND a.status = 'ATTRIBUTED' AND a.current_flag = 1
			  AND COALESCE(a.review_status, 'PENDING') != 'REDUCED'
			  AND (COALESCE(a.over_limit, 0) = 0 OR a.review_status = 'APPROVED')
			""")
	long countSettlementEligibleForTaskDate(@Param("taskId") String taskId, @Param("date") LocalDate date);

	@Select("""
			SELECT COUNT(*)
			FROM scx_attribution_result a
			JOIN scx_raw_event e ON e.id = a.conversion_event_id
			WHERE e.task_id = #{taskId} AND e.event_type = #{eventType} AND DATE(e.occurred_at) = #{date}
			  AND a.status = 'ATTRIBUTED' AND a.current_flag = 1
			  AND COALESCE(a.review_status, 'PENDING') != 'REDUCED'
			  AND (COALESCE(a.over_limit, 0) = 0 OR a.review_status = 'APPROVED')
			""")
	long countSettlementEligibleForTaskEventDate(@Param("taskId") String taskId, @Param("eventType") String eventType,
			@Param("date") LocalDate date);

}
