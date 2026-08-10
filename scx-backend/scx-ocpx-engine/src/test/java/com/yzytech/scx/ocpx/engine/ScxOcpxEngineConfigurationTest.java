package com.yzytech.scx.ocpx.engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScxOcpxEngineConfigurationTest {

	@Test
	void keepsEngineIdentityAndPublicContextPathAligned() throws IOException {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("engine",
				new ClassPathResource("application.yml"));

		assertThat(sources).isNotEmpty();
		PropertySource<?> source = sources.get(0);
		assertThat(source.getProperty("spring.application.name")).isEqualTo("scx-ocpx-engine");
		assertThat(source.getProperty("spring.config.import")).isEqualTo("classpath:ocpx-protocol-crypto.yml");
		assertThat(source.getProperty("spring.webflux.base-path")).isEqualTo("/ocpx");
		assertThat(source.getProperty("scx.ocpx.public-base-url")).isEqualTo("http://127.0.0.1:10000/ocpx");
	}

	@Test
	void keepsManagementLogConfigurationOutsideTheEngineClasspath() {
		ClassLoader classLoader = getClass().getClassLoader();

		assertThat(classLoader.getResource(
				"com/yzytech/scx/common/log/appender/BoundedSanitizedLogAppender.class"))
			.isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/common/log/config/BootSysLogConfig.class")).isNull();
		assertThat(classLoader.getResource("com/yzytech/scx/system/api/remote/RemoteSysLogService.class")).isNull();
	}

}
