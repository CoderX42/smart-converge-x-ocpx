package com.yzytech.scx.ocpx.feedback.delivery.phoenix;

import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Component;

/** 凤凰新闻 GET 客户端边界，便于单元测试隔离真实供应商请求。 */
@Component
public class PhoenixHttpClient {

	public NetUtil.HttpGetResult get(String url) {
		return NetUtil.httpGetResult(url);
	}

}
