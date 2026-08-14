package com.yzytech.scx.ocpx.feedback.web;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackIngressAdapterTest {

	@Test
	void mapsScxPrimaryKeysAndLegacyParameters() {
		FeedbackIngressRequest request = new FeedbackIngressRequest();
		request.setMId("media-id");
		request.setTId("task-id");
		request.setAId("advertiser-id");
		request.setLId("link-id");
		request.setEvent("CLK");
		request.setRequest_id("request-1");
		request.setCallback_url("https://media.example/callback?a=1");
		request.setIdfa_md5("idfa-md5-1");
		request.setCaid_list("caid-list-1");
		request.setAaid("aaid-1");
		request.setAndroidid_md5("androidid-md5-1");
		request.setClickid("click-1");
		request.setOs("0");

		FeedbackReq result = new FeedbackIngressAdapter().toFeedback(request);

		assertThat(result.getMId()).isEqualTo("media-id");
		assertThat(result.getTId()).isEqualTo("task-id");
		assertThat(result.getAId()).isEqualTo("advertiser-id");
		assertThat(result.getLId()).isEqualTo("link-id");
		assertThat(result.getEvent()).isEqualTo(EventTypeEnum.CLICK);
		assertThat(result.getOs()).isEqualTo(OsTypeEnum.ANDROID);
		assertThat(result.getIdfaMd5()).isEqualTo("idfa-md5-1");
		assertThat(result.getCaidList()).isEqualTo("caid-list-1");
		assertThat(result.getAaid()).isEqualTo("aaid-1");
		assertThat(result.getAndroididMd5()).isEqualTo("androidid-md5-1");
		assertThat(result.getClickId()).isEqualTo("click-1");
		assertThat(result.getCallbackUrl()).isEqualTo("https%3A%2F%2Fmedia.example%2Fcallback%3Fa%3D1");
	}

	@Test
	void removesMacrosAndFallsBackToMd5RequestId() {
		FeedbackIngressRequest request = new FeedbackIngressRequest();
		request.setEvent("IMP");
		request.setRequest_id("__REQUEST_ID__");
		request.setCaid("__CAID__");
		request.setIdfa_md5("__IDFA_MD5__");
		request.setCaid_list("__CAID_LIST__");
		request.setAaid("__AAID__");
		request.setAndroidid_md5("__ANDROIDID_MD5__");
		request.setClickid("__CLICKID__");

		FeedbackReq result = new FeedbackIngressAdapter().toFeedback(request);

		assertThat(result.getReqId()).matches("[0-9a-f]{32}");
		assertThat(result.getCaid()).isNull();
		assertThat(result.getIdfaMd5()).isNull();
		assertThat(result.getCaidList()).isNull();
		assertThat(result.getAaid()).isNull();
		assertThat(result.getAndroididMd5()).isNull();
		assertThat(result.getClickId()).isNull();
	}

}
