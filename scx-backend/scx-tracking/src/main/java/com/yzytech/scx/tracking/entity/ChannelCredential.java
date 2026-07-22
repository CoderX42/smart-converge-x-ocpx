package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_channel_credential")
public class ChannelCredential {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String channelClientId;

	private String keyVersion;

	private String encryptedSecret;

	private String secretIv;

	private String status;

	private LocalDateTime validFrom;

	private LocalDateTime validUntil;

	private LocalDateTime createTime;

}
