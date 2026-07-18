package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RtaDecisionLogMapper extends BaseMapper<RtaDecisionLog> {

	@Select("SELECT * FROM scx_rta_decision_log WHERE task_id=#{taskId} AND request_id=#{requestId} LIMIT 1")
	RtaDecisionLog selectByTaskAndRequest(@Param("taskId") String taskId, @Param("requestId") String requestId);

}
