package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_user_scope_revision")
public class AdxUserScopeRevision {

	@TableId(type = IdType.INPUT)
	private String userId;

	private Long version;

	private String updateBy;

	private LocalDateTime updateTime;

}
