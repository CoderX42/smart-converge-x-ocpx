package com.yzytech.scx.ocpx.feedback.delivery.weifeng;

import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Component;

/** 微风互动 GET 客户端边界，测试时由本地 Mock 替代真实网络。 */
@Component
public class WeifengHttpClient {

	public NetUtil.HttpGetResult get(String url) {
		return NetUtil.httpGetResult(url);
	}

}
