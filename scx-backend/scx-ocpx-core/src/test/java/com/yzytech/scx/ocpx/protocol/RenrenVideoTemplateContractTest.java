package com.yzytech.scx.ocpx.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RenrenVideoTemplateContractTest {

	@Test
	void acceptsStrictAndroidAndIosTemplates() {
		var android = RenrenVideoTemplateContract.requireValid(androidTemplate(),
				RenrenVideoTemplateContract.ANDROID_PLATFORM);
		var ios = RenrenVideoTemplateContract.requireValid(iosTemplate(), RenrenVideoTemplateContract.IOS_PLATFORM);

		assertThat(android.parameters()).extracting(RenrenVideoTemplateContract.QueryParameter::name)
			.contains("userId", "imeiMd5", "oaid", "oaidMd5", "callback");
		assertThat(ios.parameters()).extracting(RenrenVideoTemplateContract.QueryParameter::value)
			.contains("__CAID__", "__IDFA__", "__CALLBACK__");
	}

	@Test
	void rejectsMixedPlatformMissingIdentityDuplicateAndUnknownMacro() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(androidTemplate(), 3))
			.withMessage("RENREN_VIDEO_TEMPLATE_PLATFORM_INVALID");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(commonTemplate(),
					RenrenVideoTemplateContract.IOS_PLATFORM))
			.withMessage("RENREN_VIDEO_TEMPLATE_IOS_IDENTITY_MISSING");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(androidTemplate() + "&oaid=duplicate",
					RenrenVideoTemplateContract.ANDROID_PLATFORM))
			.withMessage("RENREN_VIDEO_TEMPLATE_PARAMETER_DUPLICATE");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(androidTemplate() + "&x=__UNKNOWN__",
					RenrenVideoTemplateContract.ANDROID_PLATFORM))
			.withMessage("RENREN_VIDEO_TEMPLATE_MACRO_INVALID");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(androidTemplate() + "&x=__unknown__",
					RenrenVideoTemplateContract.ANDROID_PLATFORM))
			.withMessage("RENREN_VIDEO_TEMPLATE_MACRO_INVALID");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(androidTemplate() + "&x={ua}",
					RenrenVideoTemplateContract.ANDROID_PLATFORM))
			.withMessage("RENREN_VIDEO_TEMPLATE_MACRO_INVALID");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(androidTemplate() + "&idfa=__IDFA__",
					RenrenVideoTemplateContract.ANDROID_PLATFORM))
			.withMessage("RENREN_VIDEO_TEMPLATE_PLATFORM_MACRO_MISMATCH");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(iosTemplate() + "&oaid=__OAID__",
					RenrenVideoTemplateContract.IOS_PLATFORM))
			.withMessage("RENREN_VIDEO_TEMPLATE_PLATFORM_MACRO_MISMATCH");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RenrenVideoTemplateContract.requireValid(commonTemplate() + "&caid=fixed",
					RenrenVideoTemplateContract.IOS_PLATFORM))
			.withMessage("RENREN_VIDEO_TEMPLATE_IOS_IDENTITY_MISSING");
	}

	private String androidTemplate() {
		return commonTemplate() + "&imeiMd5=__IMEI__&oaid=__OAID__&oaidMd5=__OAID_MD5__"
				+ "&androidId=__ANDROID_ID__&androidIdMd5=__ANDROID_ID_MD5__";
	}

	private String iosTemplate() {
		return commonTemplate() + "&caid=__CAID__&idfa=__IDFA__&idfaMd5=__IDFA_MD5__";
	}

	private String commonTemplate() {
		return "http://monitor.example.com/ad-action/click?userId=123&ts=__TS__&ip=__IP__&os=__OS__"
				+ "&ua=__UA__&callback=__CALLBACK__&requestId=__REQID__&clickId=__CLICK_ID__";
	}

}
