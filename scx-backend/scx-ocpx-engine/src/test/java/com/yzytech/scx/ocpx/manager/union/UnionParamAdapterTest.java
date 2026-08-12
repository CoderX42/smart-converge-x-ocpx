package com.yzytech.scx.ocpx.manager.union;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.constant.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.index.ChannelMonitorIndex;
import com.yzytech.scx.ocpx.manager.union.dto.UnionCallbackReq;
import com.yzytech.scx.ocpx.util.AesUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class UnionParamAdapterTest {

	private final ChannelMonitorIndex monitorIndex = new ChannelMonitorIndex();

	@AfterEach
	void clearIndex() {
		monitorIndex.clear();
	}

	@Test
	void roundTripsTheAesCallbackAndKeepsTheScxIds() {
		UnionParamAdapter adapter = adapter();
		FeedbackReq feedback = FeedbackReq.builder()
			.chid("media-id")
			.mid("task-id")
			.dspid("advertiser-id")
			.linkid("link-id")
			.reqId("request-1")
			.build();

		String callbackUrl = adapter.getCallbackUrl(feedback);
		String ext = callbackUrl.substring(callbackUrl.indexOf("ext=") + 4, callbackUrl.indexOf("&s="));
		String clearText = AesUtil.decryptBase64(ext, "local-dev-key-01");

		assertThat(callbackUrl).endsWith("&s=link-id_task-id");
		assertThat(JSON.parseObject(clearText, FeedbackReq.class))
			.extracting(FeedbackReq::getChid, FeedbackReq::getMid, FeedbackReq::getDspid, FeedbackReq::getLinkid)
			.containsExactly("media-id", "task-id", "advertiser-id", "link-id");

		UnionCallbackReq callback = new UnionCallbackReq();
		callback.setExt(ext);
		callback.setEvent_type(4);
		callback.setEvent_time(1700000000L);
		CallbackReq result = adapter.getCallbackReq(callback);
		assertThat(result.getActionType()).isEqualTo(ActionTypeEnum.REGISTER);
		assertThat(result.getActionTime()).isEqualTo(1700000000L);
		assertThat(result.getFeedback().getReqId()).isEqualTo("request-1");
	}

	@Test
	void replacesLegacyMacrosAndKeepsTheFixedSignatureVector() {
		UnionParamAdapter adapter = adapter();
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-id");
		task.setTaskCode("BIZ-1");
		monitorIndex.add(task.getId(), task);
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setBidUrl("https://adv.example/click?rid=__REQUEST_ID__&oaid=__OAID__&biz=__BIZ_ID__");
		advertiser.setCredentialConfigKey("local-signing-key");
		FeedbackReq feedback = FeedbackReq.builder().mid("task-id").reqId("request-1").oaid("oaid-1").build();

		String result = adapter.getReqUrl(advertiser, feedback);

		assertThat(result).isEqualTo("https://adv.example/click?rid=request-1&oaid=oaid-1&biz=BIZ-1"
				+ "&signature=c3bd8e8cc706ed27f9cff9a0cc0df0fe");
	}

	private UnionParamAdapter adapter() {
		UnionParamAdapter adapter = new UnionParamAdapter(monitorIndex);
		ReflectionTestUtils.setField(adapter, "domainName", "http://127.0.0.1:10000/ocpx");
		ReflectionTestUtils.setField(adapter, "callbackAesKey", "local-dev-key-01");
		return adapter;
	}

}
