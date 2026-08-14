package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Component;

/** AERIM GET 客户端边界，便于使用本地 Mock 验证协议而不发真实请求。 */
@Component
public class AerimHttpClient {

	public NetUtil.HttpGetResult get(String url) {
		return NetUtil.httpGetResult(url);
	}

}
