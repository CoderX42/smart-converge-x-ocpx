package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_callback_job")
public class CallbackJob {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String attributionResultId;

	private String actionType;

	private String targetRef;

	private String adapterCode;

	private String payloadJson;

	private String payloadHash;

	private String status;

	private Integer attempts;

	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private LocalDateTime nextRetryAt;

	private String lockedBy;

	private LocalDateTime lockedUntil;

	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private String lastErrorCode;

	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private String lastErrorMessage;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
