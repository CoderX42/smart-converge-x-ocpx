package com.yzytech.scx.ocpx.callback.delivery.mi;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.delivery.DownstreamDeliveryService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 小米媒体转化回传实现（SGYZ MiServiceImpl 平移）。
 *
 * <p>
 * 仅激活、注册、付费、下单四类动作回传；签名 secretKey 取监测任务 {@code bizId}（JSON 形态取
 * {@code secretKey} 键），请求为固定地址的 HTTP GET。
 */
@Service
public class MiDownstreamDeliveryService extends DownstreamDeliveryService {

	private final OcpxMonitorTaskIndex monitorTaskIndex;

	public MiDownstreamDeliveryService(OcpxMonitorTaskIndex monitorTaskIndex) {
		this.monitorTaskIndex = monitorTaskIndex;
	}

	@Override
	public void deliver(CallbackReq request) {
		String eventType = matchEventType(request.getActionType());
		if (eventType == null) {
			log.info("mi event null:{}", JSON.toJSONString(request));
			return;
		}

		String deviceId;
		if (request.getFeedback().getImei() != null) {
			deviceId = "&imei=" + request.getFeedback().getImei();
		}
		else {
			deviceId = "&oaid=" + request.getFeedback().getOaid();
		}

		OcpxMonitorTask monitorTask = monitorTaskIndex.get(request.getFeedback().getTId());
		String secretKey = "";
		if (monitorTask != null) {
			String bizId = monitorTask.getBizId();
			if (!bizId.startsWith("{")) {
				secretKey = bizId;
			}
			else {
				Map<String, Object> map = JSON.parseObject(bizId);
				secretKey = (String) map.get("secretKey");
			}
		}

		String params = "callback=" + request.getFeedback().getExt() + deviceId + "&conv_time="
				+ request.getActionTime() + "&convType=" + matchEventType(request.getActionType());
		String sign = DigestUtils.md5DigestAsHex((params + secretKey).getBytes(StandardCharsets.UTF_8));

		String callbackUrl = "http://trail.e.mi.com/api/callback?" + params + "&sign=" + sign;
		callbackUrl = callbackUrl + "&event_type=" + eventType;
		String content = NetUtil.httpGet(callbackUrl, null);
		if (StringUtils.hasText(content)) {
			log.info("mi cl data:{}", content);
		}
	}

	private String matchEventType(ActionTypeEnum actionType) {
		// @formatter:off
		return switch (actionType) {
			case ACTIVATE -> "APP_ACTIVE";
			case REGISTER -> "APP_REGISTER";
			case PAYMENT -> "APP_NEW_USER_PURCHASE";
			case ORDER -> "APP_COMPLETE_ORDER";
			default -> null;
		};
		// @formatter:on
	}

}
