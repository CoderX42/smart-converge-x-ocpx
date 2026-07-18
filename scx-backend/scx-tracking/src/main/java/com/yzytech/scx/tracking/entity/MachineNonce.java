package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_machine_nonce")
public class MachineNonce {

	@TableId
	private String nonce;

	private LocalDateTime expiresAt;

	private LocalDateTime createTime;

}
