package com.yzytech.scx.ocpx.feedback.web.mi;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiParamAdapterTest {

	@Test
	void mapsScxRouteIdsAndMiDialectFields() {
		MiReq req = new MiReq();
		req.setMId("media-id");
		req.setTId("task-id");
		req.setAId("advertiser-id");
		req.setLId("link-id");
		req.setEvent("CLK");
		req.setRequest_id("request-1");
		req.setCallback_url("https://media.example/callback?a=1&b=2");
		req.setOnce_encode_sua("encoded-ua");
		req.setUa("Redmi K50");
		req.setCallback("mi-callback-ext");
		req.setOs("0");
		req.setImei("imei-1");
		req.setImei_md5("imei-md5-1");
		req.setOaid("oaid-1");
		req.setOaid_md5("oaid-md5-1");
		req.setIdfa_md5("idfa-md5-1");
		req.setCaid_list("caid-list-1");
		req.setAaid("aaid-1");
		req.setClickid("click-1");
		req.setMts("1700000000000");
		req.setAdv_id("adv-1");

		FeedbackReq result = MiParamAdapter.getFeedbackReq(req);

		assertThat(result.getMId()).isEqualTo("media-id");
		assertThat(result.getTId()).isEqualTo("task-id");
		assertThat(result.getAId()).isEqualTo("advertiser-id");
		assertThat(result.getLId()).isEqualTo("link-id");
		assertThat(result.getEvent()).isEqualTo(EventTypeEnum.CLICK);
		assertThat(result.getReqId()).isEqualTo("request-1");
		// SGYZ 原有映射：ua 取 once_encode_sua，model 取原始 ua。
		assertThat(result.getUa()).isEqualTo("encoded-ua");
		assertThat(result.getModel()).isEqualTo("Redmi K50");
		assertThat(result.getExt()).isEqualTo("mi-callback-ext");
		assertThat(result.getOs()).isEqualTo(OsTypeEnum.ANDROID);
		assertThat(result.getImeiMd5()).isEqualTo("imei-md5-1");
		assertThat(result.getIdfaMd5()).isEqualTo("idfa-md5-1");
		assertThat(result.getCaidList()).isEqualTo("caid-list-1");
		assertThat(result.getAaid()).isEqualTo("aaid-1");
		assertThat(result.getClickId()).isEqualTo("click-1");
		assertThat(result.getTsMs()).isEqualTo("1700000000000");
		assertThat(result.getAdvId()).isEqualTo("adv-1");
		assertThat(result.getCallbackUrl()).isEqualTo("https%3A%2F%2Fmedia.example%2Fcallback%3Fa%3D1%26b%3D2");
	}

	@Test
	void fallsBackToWholePayloadMd5WhenRequestIdIsMissing() {
		MiReq req = new MiReq();
		req.setEvent("IMP");
		req.setRequest_id("__REQUEST_ID__");

		FeedbackReq result = MiParamAdapter.getFeedbackReq(req);

		assertThat(result.getReqId()).matches("[0-9a-f]{32}");
	}

	@Test
	void matchesMiOsCodesAndDefaultsToAndroid() {
		MiReq req = new MiReq();
		req.setEvent("CLK");
		req.setRequest_id("request-1");

		req.setOs("1");
		assertThat(MiParamAdapter.getFeedbackReq(req).getOs()).isEqualTo(OsTypeEnum.IOS);
		req.setOs("0");
		assertThat(MiParamAdapter.getFeedbackReq(req).getOs()).isEqualTo(OsTypeEnum.ANDROID);
		req.setOs("9");
		assertThat(MiParamAdapter.getFeedbackReq(req).getOs()).isEqualTo(OsTypeEnum.UNKNOWN);
		// SGYZ 行为：os 缺失时按 ANDROID 处理。
		req.setOs(null);
		assertThat(MiParamAdapter.getFeedbackReq(req).getOs()).isEqualTo(OsTypeEnum.ANDROID);
	}

}
