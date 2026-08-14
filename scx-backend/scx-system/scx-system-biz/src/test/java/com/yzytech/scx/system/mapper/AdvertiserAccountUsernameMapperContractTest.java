package com.yzytech.scx.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AdvertiserAccountUsernameMapperContractTest {

	@Test
	void usernameOccupancyNormalizesWhitespaceAndIncludesLogicallyDeletedUsers() throws Exception {
		try (InputStream input = getClass().getResourceAsStream("/mapper/SysUserMapper.xml")) {
			assertThat(input).as("system user mapper resource").isNotNull();
			String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			int start = xml.indexOf("<select id=\"countByUsernameIncludingDeleted\"");
			int end = xml.indexOf("</select>", start);
			assertThat(start).isNotNegative();
			assertThat(end).isGreaterThan(start);
			assertThat(xml.substring(start, end)).contains("TRIM(username) = #{username}").doesNotContain("del_flag");
		}
	}

}
