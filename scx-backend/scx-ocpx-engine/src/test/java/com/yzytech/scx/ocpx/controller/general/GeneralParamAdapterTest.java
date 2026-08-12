package com.yzytech.scx.ocpx.controller.general;

import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.constant.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.constant.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.controller.general.dto.GeneralReq;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralParamAdapterTest {

	@Test
	void mapsScxPrimaryKeysAndLegacyParameters() {
		GeneralReq request = new GeneralReq();
		request.setChid("media-id");
		request.setMid("task-id");
		request.setDspid("advertiser-id");
		request.setLinkid("link-id");
		request.setEvent("CLK");
		request.setRequest_id("request-1");
		request.setCallback_url("https://media.example/callback?a=1");
		request.setOs("0");

		FeedbackReq result = GeneralParamAdapter.getFeedbackReq(request);

		assertThat(result.getChid()).isEqualTo("media-id");
		assertThat(result.getMid()).isEqualTo("task-id");
		assertThat(result.getDspid()).isEqualTo("advertiser-id");
		assertThat(result.getLinkid()).isEqualTo("link-id");
		assertThat(result.getEvent()).isEqualTo(EventTypeEnum.CLICK);
		assertThat(result.getOs()).isEqualTo(OsTypeEnum.ANDROID);
		assertThat(result.getCallbackUrl()).isEqualTo("https%3A%2F%2Fmedia.example%2Fcallback%3Fa%3D1");
	}

	@Test
	void removesMacrosAndFallsBackToMd5RequestId() {
		GeneralReq request = new GeneralReq();
		request.setEvent("IMP");
		request.setRequest_id("__REQUEST_ID__");
		request.setCaid("__CAID__");

		FeedbackReq result = GeneralParamAdapter.getFeedbackReq(request);

		assertThat(result.getReqId()).matches("[0-9a-f]{32}");
		assertThat(result.getCaid()).isNull();
	}

}
