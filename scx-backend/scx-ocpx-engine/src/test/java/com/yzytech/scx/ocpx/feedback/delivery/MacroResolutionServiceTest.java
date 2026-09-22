package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class MacroResolutionServiceTest {

	private final MacroResolutionService service = new MacroResolutionService();

	@ParameterizedTest
	@CsvSource({ "ANDROID,oaid_md5", "ANDROID,oaidmd5", "ANDROID,imei_md5", "ANDROID,imeimd5", "ANDROID,androidid_md5",
			"ANDROID,android_id_md5", "ANDROID,androidmd5", "ANDROID,sid_md5", "IOS,idfa_md5", "IOS,caid",
			"IOS,caid_list", "IOS,caid1", "IOS,caid2", "IOS,last_caid", "IOS,prev_caid" })
	void acceptsConcreteSupportedIdentityParameters(OsTypeEnum os, String parameter) {
		assertThat(service.hasForwardedIdentity("https://partner.example/click?" + parameter + "=device-value", os))
			.isTrue();
	}

	@ParameterizedTest
	@CsvSource({ "ANDROID,oaid_md5=", "ANDROID,oaid_md5=%20%09", "ANDROID,oaid_md5=__OAID_MD5__",
			"ANDROID,oaid_md5=%5F%5FOAID_MD5%5F%5F", "ANDROID,idfa_md5=device-value", "IOS,oaid_md5=device-value",
			"IOS,caid_version=20220111", "IOS,caid_unknown=device-value", "IOS,caid_list=+", "IOS,mac_md5=device-value",
			"UNKNOWN,oaid_md5=device-value", "ANDROID,request_id=request-id", "ANDROID,ip=203.0.113.1&ua=%20",
			"ANDROID,ip=__IP__&ua=agent", "IOS,ip=203.0.113.1", "IOS,ua=agent" })
	void rejectsEmptyPlaceholderUnrelatedAndWrongPlatformParameters(OsTypeEnum os, String query) {
		assertThat(service.hasForwardedIdentity("https://partner.example/click?" + query, os)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(OsTypeEnum.class)
	void permitsOnlyCompleteConcreteIpAndUaFallback(OsTypeEnum os) {
		FeedbackReq feedback = FeedbackReq.builder().os(os).ip("203.0.113.1").ua("Test Agent").build();
		assertThat(service.hasInboundIdentity(feedback)).isTrue();
		assertThat(service.hasForwardedIdentity("https://partner.example/click?ipv4=203.0.113.1&ua=Test+Agent", os))
			.isTrue();
		feedback.setUa("__UA__");
		assertThat(service.hasInboundIdentity(feedback)).isFalse();
	}

	@Test
	void alignsHashOnlyInboundAndForwardedIdentitiesWithoutAcceptingPlaceholders() {
		FeedbackReq android = FeedbackReq.builder().os(OsTypeEnum.ANDROID).oaidMd5("oaid-md5").build();
		FeedbackReq ios = FeedbackReq.builder().os(OsTypeEnum.IOS).idfaMd5("idfa-md5").build();
		assertThat(service.hasInboundIdentity(android)).isTrue();
		assertThat(service.hasInboundIdentity(ios)).isTrue();
		android.setOaidMd5("__OAID_MD5__");
		ios.setIdfaMd5(" ");
		assertThat(service.hasInboundIdentity(android)).isFalse();
		assertThat(service.hasInboundIdentity(ios)).isFalse();
	}

	@Test
	void rejectsMalformedAndMissingQueryStrings() {
		assertThat(service.hasForwardedIdentity("https://partner.example/click?oaid_md5=%zz", OsTypeEnum.ANDROID))
			.isFalse();
		assertThat(service.hasForwardedIdentity("https://partner.example/click", OsTypeEnum.ANDROID)).isFalse();
		assertThat(service.hasForwardedIdentity(null, OsTypeEnum.ANDROID)).isFalse();
	}

}
