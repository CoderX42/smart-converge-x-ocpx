package com.yzytech.scx.reporting.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ClickSensitiveDetailSerializationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void exposesOnlyMaskedBusinessIdentifiersAndPresenceFlags() throws Exception {
		ClickSensitiveDetail detail = new ClickSensitiveDetail();
		detail.setClickIdMasked("click-***");
		detail.setCampaignIdMasked("campai***");
		detail.setCreativeIdMasked("creati***");
		detail.setOaidProtected(true);
		detail.setOaidMd5Protected(true);
		detail.setIpProtected(true);

		String json = objectMapper.writeValueAsString(detail);

		assertThat(json).contains("\"clickIdMasked\":\"click-***\"", "\"campaignIdMasked\":\"campai***\"",
				"\"creativeIdMasked\":\"creati***\"", "\"oaidProtected\":true", "\"ipProtected\":true");
		assertThat(json).doesNotContain("\"clickId\":", "\"campaignId\":", "\"creativeId\":", "\"oaid\":",
				"\"oaidMd5\":", "\"ip\":");
	}

}
