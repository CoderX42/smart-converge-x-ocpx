package com.yzytech.scx.ocpx.callback.web.phoenix;

import lombok.Data;

/** {@code /phoenix/callback} 接收的凤凰新闻转化参数。 */
@Data
public class PhoenixCallbackRequest {

	private String kid;

	private String ext;

	private String event_type;

}
