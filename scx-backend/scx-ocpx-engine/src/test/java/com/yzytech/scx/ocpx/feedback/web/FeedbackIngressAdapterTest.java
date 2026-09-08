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
		request.setCaid2("old-caid-1");
		request.setLast_caid("last-caid-1");
		request.setPrev_caid("prev-caid-1");
		request.setAaid("aaid-1");
		request.setAndroidid_md5("androidid-md5-1");
		request.setClickid("click-1");
		request.setIpv6("2408:1::1");
		request.setOsv("13.1.1");
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
		assertThat(result.getCaid2()).isEqualTo("old-caid-1");
		assertThat(result.getLastCaid()).isEqualTo("last-caid-1");
		assertThat(result.getPrevCaid()).isEqualTo("prev-caid-1");
		assertThat(result.getAaid()).isEqualTo("aaid-1");
		assertThat(result.getAndroididMd5()).isEqualTo("androidid-md5-1");
		assertThat(result.getClickId()).isEqualTo("click-1");
		assertThat(result.getIpv6()).isEqualTo("2408:1::1");
		assertThat(result.getOsv()).isEqualTo("13.1.1");
		assertThat(result.getCallbackUrl()).isEqualTo("https%3A%2F%2Fmedia.example%2Fcallback%3Fa%3D1");
	}

	@Test
	void removesMacrosAndFallsBackToMd5RequestId() {
		FeedbackIngressRequest request = new FeedbackIngressRequest();
		request.setEvent("IMP");
		request.setRequest_id("__REQUEST_ID__");
		request.setCallback_url("__CALLBACK__");
		request.setCaid("__CAID__");
		request.setIdfa_md5("__IDFA_MD5__");
		request.setCaid_list("__CAID_LIST__");
		request.setCaid2("__CAID2__");
		request.setAaid("__AAID__");
		request.setAndroidid_md5("__ANDROIDID_MD5__");
		request.setAndroidid("__ANDROID_ID__");
		request.setClickid("__CLICKID__");
		request.setIpv6("__IPV6__");
		request.setModel("__MODEL__");
		request.setOsv("__OSV__");

		FeedbackReq result = new FeedbackIngressAdapter().toFeedback(request);

		assertThat(result.getReqId()).matches("[0-9a-f]{32}");
		assertThat(result.isRequestIdGenerated()).isTrue();
		assertThat(result.getCallbackUrl()).isNull();
		assertThat(result.getCaid()).isNull();
		assertThat(result.getIdfaMd5()).isNull();
		assertThat(result.getCaidList()).isNull();
		assertThat(result.getCaid2()).isNull();
		assertThat(result.getAaid()).isNull();
		assertThat(result.getAndroididMd5()).isNull();
		assertThat(result.getAndroidid()).isNull();
		assertThat(result.getClickId()).isNull();
		assertThat(result.getIpv6()).isNull();
		assertThat(result.getModel()).isNull();
		assertThat(result.getOsv()).isNull();
	}

	@Test
	void treatsCaseInsensitiveRequestIdMacroAsMissing() {
		FeedbackIngressRequest request = new FeedbackIngressRequest();
		request.setEvent("IMP");
		request.setRequest_id(" __request_id__ ");

		FeedbackReq result = new FeedbackIngressAdapter().toFeedback(request);

		assertThat(result.getReqId()).matches("[0-9a-f]{32}");
		assertThat(result.isRequestIdGenerated()).isTrue();
	}

	@Test
	void acceptsTextualAndNumericOsValues() {
		FeedbackIngressRequest ios = new FeedbackIngressRequest();
		ios.setEvent("CLK");
		ios.setOs(" ios ");
		assertThat(new FeedbackIngressAdapter().toFeedback(ios).getOs()).isEqualTo(OsTypeEnum.IOS);

		FeedbackIngressRequest android = new FeedbackIngressRequest();
		android.setEvent("CLK");
		android.setOs("ANDROID");
		assertThat(new FeedbackIngressAdapter().toFeedback(android).getOs()).isEqualTo(OsTypeEnum.ANDROID);

		FeedbackIngressRequest numericIos = new FeedbackIngressRequest();
		numericIos.setEvent("CLK");
		numericIos.setOs("1");
		assertThat(new FeedbackIngressAdapter().toFeedback(numericIos).getOs()).isEqualTo(OsTypeEnum.IOS);
	}

}
