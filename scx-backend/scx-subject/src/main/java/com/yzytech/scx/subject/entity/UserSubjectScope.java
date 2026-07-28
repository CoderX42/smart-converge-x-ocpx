package com.yzytech.scx.subject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_user_subject_scope")
public class UserSubjectScope {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String userId;

	private String subjectType;

	private String subjectId;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
