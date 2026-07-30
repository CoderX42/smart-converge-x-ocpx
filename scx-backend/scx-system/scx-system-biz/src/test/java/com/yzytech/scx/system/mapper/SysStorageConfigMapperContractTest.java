package com.yzytech.scx.system.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SysStorageConfigMapperContractTest {

	@Test
	void selectConfigOnlyUsesColumnsFromTheCurrentStorageSchema() throws IOException {
		InputStream resource = SysStorageConfigMapperContractTest.class
			.getResourceAsStream("/mapper/SysStorageConfigMapper.xml");

		assertThat(resource).as("storage mapper resource").isNotNull();
		try (resource) {
			String mapperXml = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(mapperXml)
				.doesNotContain("sys_storage_config.is_https")
				.contains("sys_storage_config.style_access_enabled", "sys_storage_config.domain");
		}
	}

}
