package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_user_task_scope")
public class UserTaskScope {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@NotBlank
	private String userId;

	@NotBlank
	private String mediaId;

	@NotBlank
	private String taskId;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
