package com.yzytech.scx.ocpx.callback.delivery.mi;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;

class MiDownstreamDeliveryServiceTest {

	private final OcpxMonitorTaskIndex monitorTaskIndex = new OcpxMonitorTaskIndex();

	private final MiDownstreamDeliveryService service = new MiDownstreamDeliveryService(monitorTaskIndex);

	@AfterEach
	void clearIndex() {
		monitorTaskIndex.clear();
	}

	@Test
	void buildsTheMiCallbackUrlWithImeiAndPlainSecretKey() {
		OcpxMonitorTask task = task("task-id", "mi-secret");
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			netUtil.when(() -> NetUtil.httpGet(any(), isNull())).thenReturn("{\"code\":0}");

			service.deliver(request(ActionTypeEnum.ACTIVATE, "imei-1", "oaid-1"));

			String params = "callback=mi-ext&imei=imei-1&conv_time=1700000000123&convType=APP_ACTIVE";
			String sign = DigestUtils.md5DigestAsHex((params + "mi-secret").getBytes(StandardCharsets.UTF_8));
			String expected = "http://trail.e.mi.com/api/callback?" + params + "&sign=" + sign
					+ "&event_type=APP_ACTIVE";
			netUtil.verify(() -> NetUtil.httpGet(expected, null));
		}
	}

	@Test
	void fallsBackToOaidWhenImeiIsMissing() {
		task("task-id", "mi-secret");
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			service.deliver(request(ActionTypeEnum.ACTIVATE, null, "oaid-1"));

			String params = "callback=mi-ext&oaid=oaid-1&conv_time=1700000000123&convType=APP_ACTIVE";
			String sign = DigestUtils.md5DigestAsHex((params + "mi-secret").getBytes(StandardCharsets.UTF_8));
			String expected = "http://trail.e.mi.com/api/callback?" + params + "&sign=" + sign
					+ "&event_type=APP_ACTIVE";
			netUtil.verify(() -> NetUtil.httpGet(expected, null));
		}
	}

	@Test
	void readsTheSecretKeyFromAJsonShapedBizId() {
		task("task-id", "{\"secretKey\":\"json-secret\",\"trackingId\":\"trk-1\"}");
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			service.deliver(request(ActionTypeEnum.REGISTER, "imei-1", null));

			String params = "callback=mi-ext&imei=imei-1&conv_time=1700000000123&convType=APP_REGISTER";
			String sign = DigestUtils.md5DigestAsHex((params + "json-secret").getBytes(StandardCharsets.UTF_8));
			String expected = "http://trail.e.mi.com/api/callback?" + params + "&sign=" + sign
					+ "&event_type=APP_REGISTER";
			netUtil.verify(() -> NetUtil.httpGet(expected, null));
		}
	}

	@Test
	void signsWithAnEmptySecretWhenTheMonitorTaskIsMissing() {
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			service.deliver(request(ActionTypeEnum.PAYMENT, "imei-1", null));

			String params = "callback=mi-ext&imei=imei-1&conv_time=1700000000123&convType=APP_NEW_USER_PURCHASE";
			String sign = DigestUtils.md5DigestAsHex(params.getBytes(StandardCharsets.UTF_8));
			String expected = "http://trail.e.mi.com/api/callback?" + params + "&sign=" + sign
					+ "&event_type=APP_NEW_USER_PURCHASE";
			netUtil.verify(() -> NetUtil.httpGet(expected, null));
		}
	}

	@Test
	void mapsTheOrderActionAndSkipsUnsupportedActions() {
		task("task-id", "mi-secret");
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			service.deliver(request(ActionTypeEnum.ORDER, "imei-1", null));

			String params = "callback=mi-ext&imei=imei-1&conv_time=1700000000123&convType=APP_COMPLETE_ORDER";
			String sign = DigestUtils.md5DigestAsHex((params + "mi-secret").getBytes(StandardCharsets.UTF_8));
			String expected = "http://trail.e.mi.com/api/callback?" + params + "&sign=" + sign
					+ "&event_type=APP_COMPLETE_ORDER";
			netUtil.verify(() -> NetUtil.httpGet(expected, null));

			service.deliver(request(ActionTypeEnum.LOGIN, "imei-1", null));
			service.deliver(request(ActionTypeEnum.NEXT_STAY, "imei-1", null));
			netUtil.verifyNoMoreInteractions();
		}
	}

	private OcpxMonitorTask task(String id, String bizId) {
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId(id);
		task.setBizId(bizId);
		monitorTaskIndex.add(id, task);
		return task;
	}

	private CallbackReq request(ActionTypeEnum actionType, String imei, String oaid) {
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.imei(imei)
			.oaid(oaid)
			.ext("mi-ext")
			.build();
		return CallbackReq.builder().feedback(feedback).actionType(actionType).actionTime(1700000000123L).build();
	}

}
