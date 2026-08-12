package com.yzytech.scx.ocpx.controller.general;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.controller.ChannelService;
import com.yzytech.scx.ocpx.util.NetUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
public class GeneralServiceImpl extends ChannelService {

	@Override
	public void callback(CallbackReq request) {
		Integer eventType = request.getActionType().getIndex();
		if (eventType == null) {
			log.info("general event null:{}", JSON.toJSONString(request));
			return;
		}
		if (request.getFeedback().getCallbackUrl() == null) {
			log.info("general cl callback null");
			return;
		}
		String callbackUrl = URLDecoder.decode(request.getFeedback().getCallbackUrl(), StandardCharsets.UTF_8);
		callbackUrl = callbackUrl + "&event_type=" + eventType;
		String content = NetUtil.httpGet(callbackUrl, null);
		if (StringUtils.hasText(content)) {
			log.info("general cl data:{}", content);
		}
	}

}
