package com.yzytech.scx.tracking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.yzytech.scx.common.core.config.DateFormatConfig;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class DateFormatConfigCompatibilityTest {

	@Test
	void preservesOffsetDateTimeSupportAlongsideLegacyLocalDateFormats() throws Exception {
		Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
		new DateFormatConfig().customizer().customize(builder);
		var objectMapper = builder.build();

		OffsetDateTime utc = objectMapper.readValue("\"2026-07-22T14:12:00.123Z\"", OffsetDateTime.class);
		String json = objectMapper.writeValueAsString(OffsetDateTime.parse("2026-07-22T22:12:00+08:00"));

		assertThat(utc.toString()).isEqualTo("2026-07-22T14:12:00.123Z");
		assertThat(json).contains("2026-07-22T22:12:00+08:00");
	}

}
