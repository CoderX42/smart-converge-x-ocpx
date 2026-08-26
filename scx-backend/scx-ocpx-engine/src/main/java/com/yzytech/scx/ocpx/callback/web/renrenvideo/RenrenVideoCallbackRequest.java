package com.yzytech.scx.ocpx.callback.web.renrenvideo;

import lombok.Data;

/** {@code /renren-video/callback} 接收的人人视频转化参数。 */
@Data
public class RenrenVideoCallbackRequest {

	private String kid;

	private String ext;

	private String event;

}
