package com.yzytech.scx.ocpx.report;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.model.LogData;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackRequestDetailPersistenceContractTest {

	@Test
	void normalizedDetailSurvivesFeedbackQueueProjection() {
		FeedbackReq feedback = FeedbackReq.builder()
			.caidList("caid-list")
			.caid2("caid-2")
			.lastCaid("last-caid")
			.prevCaid("previous-caid")
			.idfaMd5("idfa-md5")
			.aaid("aaid")
			.imeiMd5("imei-md5")
			.oaidMd5("oaid-md5")
			.androididMd5("android-id-md5")
			.ipv6("2001:db8::1")
			.model("device-model")
			.osv("17.6")
			.build();

		LogData logData = JSON.parseObject(JSON.toJSONString(feedback), LogData.class);

		assertThat(logData.getCaidList()).isEqualTo("caid-list");
		assertThat(logData.getCaid2()).isEqualTo("caid-2");
		assertThat(logData.getLastCaid()).isEqualTo("last-caid");
		assertThat(logData.getPrevCaid()).isEqualTo("previous-caid");
		assertThat(logData.getIdfaMd5()).isEqualTo("idfa-md5");
		assertThat(logData.getAaid()).isEqualTo("aaid");
		assertThat(logData.getImeiMd5()).isEqualTo("imei-md5");
		assertThat(logData.getOaidMd5()).isEqualTo("oaid-md5");
		assertThat(logData.getAndroididMd5()).isEqualTo("android-id-md5");
		assertThat(logData.getIpv6()).isEqualTo("2001:db8::1");
		assertThat(logData.getModel()).isEqualTo("device-model");
		assertThat(logData.getOsv()).isEqualTo("17.6");
	}

	@Test
	void feedbackMapperWritesEveryNormalizedDetailFieldToBothTables() throws Exception {
		String mapper = resource("mapper/FeedbackMapper.xml");

		assertThat(mapper)
			.contains("device_androidid_md5", "device_idfa_md5", "aaid", "device_caid2", "device_caid_list",
					"last_caid", "prev_caid", "device_imei_md5", "device_oaid_md5", "ipv6", "model", "osv",
					"#{item.androididMd5}", "#{item.caidList}", "#{item.model}")
			.doesNotContain("raw_query", "raw_url", "#{item.sign}");
	}

	private String resource(String path) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("resource %s", path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
