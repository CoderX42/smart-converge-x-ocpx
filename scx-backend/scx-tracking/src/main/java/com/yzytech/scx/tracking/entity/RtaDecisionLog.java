package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_rta_decision_log")
public class RtaDecisionLog {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String taskId;

	private String endpointId;

	private String requestId;

	private String requestHash;

	private String decision;

	private String status;

	private String reasonCode;

	private Integer latencyMs;

	private LocalDateTime requestedAt;

	private LocalDateTime completedAt;

}
