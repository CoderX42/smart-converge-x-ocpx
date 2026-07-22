package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_click_callback_target")
public class ClickCallbackTarget {

	@TableId
	private String clickEventId;

	private String targetRef;

	private String encryptedUrl;

	private String urlIv;

	private String urlHost;

	private String urlFingerprint;

	private String keyVersion;

	private LocalDateTime createTime;

}
