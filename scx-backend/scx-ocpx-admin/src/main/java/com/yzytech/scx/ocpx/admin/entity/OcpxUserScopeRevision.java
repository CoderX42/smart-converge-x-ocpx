package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_user_scope_revision")
public class OcpxUserScopeRevision {

	@TableId(type = IdType.INPUT)
	private String userId;

	private Long version;

	private String updateBy;

	private LocalDateTime updateTime;

}
