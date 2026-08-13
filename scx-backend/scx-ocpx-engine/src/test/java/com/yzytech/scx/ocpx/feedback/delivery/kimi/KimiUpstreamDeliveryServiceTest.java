package com.yzytech.scx.ocpx.feedback.delivery.kimi;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;

class KimiUpstreamDeliveryServiceTest {

	private final OcpxAdvertiserIndex advertiserIndex = new OcpxAdvertiserIndex();

	private final OcpxMonitorTaskIndex monitorTaskIndex = new OcpxMonitorTaskIndex();

	private final KimiUpstreamDeliveryService service = service();

	@AfterEach
	void clearIndexes() {
		advertiserIndex.clear();
		monitorTaskIndex.clear();
	}

	@Test
	void buildsTheKimiCallbackUrlWithEncryptedExtAndLinkMediaState() {
		FeedbackReq feedback = feedback(EventTypeEnum.CLICK);

		String callbackUrl = service.getCallbackUrl(feedback);

		assertThat(callbackUrl).startsWith("http://127.0.0.1:10000/ocpx/kimi/callback?ext=");
		assertThat(callbackUrl).endsWith("&s=link-id_media-id");
		String ext = callbackUrl.substring(callbackUrl.indexOf("ext=") + 4, callbackUrl.indexOf("&s="));
		String clearText = AesUtil.decryptBase64(ext, "local-dev-key-01");
		assertThat(clearText).contains("\"mId\":\"media-id\"", "\"tId\":\"task-id\"", "\"aId\":\"advertiser-id\"",
				"\"lId\":\"link-id\"", "\"reqId\":\"request-1\"");
	}

	@Test
	void replacesKimiMacrosAndReadsTheTrackingIdFromAJsonShapedBizId() {
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-id");
		task.setBizId("{\"secretKey\":\"s\",\"trackingId\":\"trk 1\"}");
		monitorTaskIndex.add(task.getId(), task);
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser
			.setBidUrl("https://kimi.example/click?rid=__REQUEST_ID__&oaid=__OAID__&ua=__UA__&os=__OS__&missing=__IMEI__"
					+ "&biz=__BIZ_ID__&cb=__CALLBACK_URL__");
		FeedbackReq feedback = feedback(EventTypeEnum.CLICK);

		String requestUrl = service.getReqUrl(advertiser, feedback);

		String expectedCallback = NetUtil.strictUrlEncode(service.getCallbackUrl(feedback),
				java.nio.charset.StandardCharsets.UTF_8);
		assertThat(requestUrl)
			.isEqualTo("https://kimi.example/click?rid=request-1&oaid=oaid-1&ua=ua%20x&os=android&missing=__IMEI__"
					+ "&biz=trk%201&cb=" + expectedCallback);
	}

	@Test
	void matchesIosAndDefaultsEverythingElseToAndroid() {
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-id");
		task.setBizId("plain-biz");
		monitorTaskIndex.add(task.getId(), task);
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setBidUrl("https://kimi.example/click?os=__OS__&biz=__BIZ_ID__");
		FeedbackReq feedback = feedback(EventTypeEnum.CLICK);

		feedback.setOs(OsTypeEnum.IOS);
		assertThat(service.getReqUrl(advertiser, feedback))
			.isEqualTo("https://kimi.example/click?os=ios&biz=plain-biz");
		feedback.setOs(OsTypeEnum.ANDROID);
		assertThat(service.getReqUrl(advertiser, feedback))
			.isEqualTo("https://kimi.example/click?os=android&biz=plain-biz");
		feedback.setOs(null);
		assertThat(service.getReqUrl(advertiser, feedback))
			.isEqualTo("https://kimi.example/click?os=android&biz=plain-biz");
	}

	@Test
	void deliversOnlyClickEvents() {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-id");
		advertiser.setBidUrl("https://kimi.example/click?rid=__REQUEST_ID__");
		advertiserIndex.add(advertiser.getId(), advertiser);
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class, Mockito.CALLS_REAL_METHODS)) {
			netUtil.when(() -> NetUtil.httpGet(any(), isNull())).thenReturn("ok");

			FeedbackReq impression = feedback(EventTypeEnum.IMP);
			service.deliver(impression);
			netUtil.verifyNoInteractions();

			FeedbackReq click = feedback(EventTypeEnum.CLICK);
			service.deliver(click);
			String expectedUrl = service.getReqUrl(advertiser, click);
			netUtil.verify(() -> NetUtil.httpGet(expectedUrl, null));
		}
	}

	@Test
	void rethrowsWhenTheDeliveryFails() {
		FeedbackReq click = feedback(EventTypeEnum.CLICK);

		// 广告主索引缺失时 getReqUrl 触发空指针，SGYZ 行为为包装成 RuntimeException 继续抛出。
		assertThatThrownBy(() -> service.deliver(click)).isInstanceOf(RuntimeException.class);
	}

	private FeedbackReq feedback(EventTypeEnum event) {
		return FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.event(event)
			.reqId("request-1")
			.oaid("oaid-1")
			.ua("ua x")
			.os(OsTypeEnum.ANDROID)
			.build();
	}

	private KimiUpstreamDeliveryService service() {
		KimiUpstreamDeliveryService service = new KimiUpstreamDeliveryService(advertiserIndex, monitorTaskIndex);
		ReflectionTestUtils.setField(service, "domainName", "http://127.0.0.1:10000/ocpx");
		ReflectionTestUtils.setField(service, "callbackAesKey", "local-dev-key-01");
		return service;
	}

}
