package com.yzytech.scx.tracking.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TrackingPropertiesValidationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(
				org.springframework.boot.autoconfigure.AutoConfigurations.of(ValidationAutoConfiguration.class))
		.withUserConfiguration(PropertiesConfiguration.class);

	@Test
	void startsWhenBothRequiredTrackingKeysArePresent() {
		contextRunner
			.withPropertyValues("scx.tracking.channel-master-key=channel-key",
					"scx.tracking.device-hmac-key=device-key")
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void rejectsMissingChannelMasterKey() {
		contextRunner.withPropertyValues("scx.tracking.device-hmac-key=device-key")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsBlankDeviceHmacKey() {
		contextRunner
			.withPropertyValues("scx.tracking.channel-master-key=channel-key", "scx.tracking.device-hmac-key= ")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(TrackingProperties.class)
	static class PropertiesConfiguration {

	}

}
