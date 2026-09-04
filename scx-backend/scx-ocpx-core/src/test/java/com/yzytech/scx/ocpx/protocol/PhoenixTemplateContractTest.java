package com.yzytech.scx.ocpx.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PhoenixTemplateContractTest {

	@Test
	void acceptsActualBusinessValuesAndPlatformMacros() {
		String template = "https://api.feather.ifeng.com/d/cli?cid=123&gid=456&aid=789&mid=999&channel=abc"
				+ "&reqid=__REQID__&os=__OS__&ts=__TS__&callback=__CALLBACK__"
				+ "&oaid=__OAID__&oaid_md5=__OAIDMD5__&caid=__CAID__&idfa=__IDFA__";

		assertThat(PhoenixTemplateContract.requireValid(template, 1).parameters()).hasSize(13);
		assertThat(PhoenixTemplateContract.requireValid(template, 2).parameters()).hasSize(13);
		assertThat(PhoenixTemplateContract.requireValid(template, 3).parameters()).hasSize(13);
	}

	@Test
	void acceptsCustomPathButRejectsInvalidValuesAndUnsupportedPlatform() {
		String valid = "https://api.feather.ifeng.com/d/cli?cid=123&gid=456&aid=789&mid=999&channel=abc"
				+ "&reqid=__REQID__&os=__OS__&ts=__TS__&callback=__CALLBACK__"
				+ "&oaid=__OAID__&oaid_md5=__OAIDMD5__";

		assertThat(PhoenixTemplateContract.requireValid(valid.replace("/d/cli", "/ocpx/d/cli"), 1).uri().getPath())
				.isEqualTo("/ocpx/d/cli");
		assertThatIllegalArgumentException().isThrownBy(() -> PhoenixTemplateContract.requireValid(
				valid.replace("cid=123", "cid=__CID__"), 1)).withMessage("PHOENIX_TEMPLATE_MACRO_INVALID");
		assertThatIllegalArgumentException().isThrownBy(() -> PhoenixTemplateContract.requireValid(valid, 4))
				.withMessage("PHOENIX_TEMPLATE_PLATFORM_INVALID");
	}

	@Test
	void requiresIosCaidAndAndroidOaidMacrosByPlatform() {
		String common = "https://api.feather.ifeng.com/d/cli?cid=123&gid=456&aid=789&mid=999&channel=abc"
				+ "&reqid=__REQID__&os=__OS__&ts=__TS__&callback=__CALLBACK__";
		String android = common + "&oaid=__OAID__&oaid_md5=__OAIDMD5__";
		assertThatIllegalArgumentException().isThrownBy(() -> PhoenixTemplateContract.requireValid(android, 2))
				.withMessage("PHOENIX_TEMPLATE_DEVICE_MACRO_MISSING");
		assertThatIllegalArgumentException().isThrownBy(() -> PhoenixTemplateContract.requireValid(common + "&caid=__CAID__", 1))
				.withMessage("PHOENIX_TEMPLATE_DEVICE_MACRO_MISSING");
	}

}
