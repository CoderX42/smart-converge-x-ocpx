package com.yzytech.scx.ocpx.feedback.delivery.renrenvideo;

import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Component;

/** 人人视频 GET 客户端边界，便于隔离测试且不触发真实合作方请求。 */
@Component
public class RenrenVideoHttpClient {

	public NetUtil.HttpGetResult get(String url) {
		return NetUtil.httpGetResult(url);
	}

}
