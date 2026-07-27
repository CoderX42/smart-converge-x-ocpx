package com.yzytech.scx.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ProdRequiredConfigurationEnvironmentPostProcessorTest {

	private final ProdRequiredConfigurationEnvironmentPostProcessor processor = new ProdRequiredConfigurationEnvironmentPostProcessor();

	@Test
	void ignoresNonProductionProfiles() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("dev");

		assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
			.doesNotThrowAnyException();
	}

	@Test
	void acceptsCompleteProductionConfiguration() {
		MockEnvironment environment = productionEnvironment(requiredProperties());

		assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
			.doesNotThrowAnyException();
	}

	@Test
	void rejectsEveryMissingOrBlankProductionRequirementWithoutPrintingValues() {
		requiredProperties().forEach((missingProperty, ignored) -> {
			Map<String, String> properties = requiredProperties();
			properties.put(missingProperty, " ");
			MockEnvironment environment = productionEnvironment(properties);

			assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(environmentVariableFor(missingProperty))
				.hasMessageNotContaining("synthetic-secret");
		});
	}

	private static MockEnvironment productionEnvironment(Map<String, String> properties) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		properties.forEach(environment::setProperty);
		return environment;
	}

	private static Map<String, String> requiredProperties() {
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put("spring.datasource.dynamic.datasource.master.url", "jdbc:mysql://db/scx");
		properties.put("spring.datasource.dynamic.datasource.master.username", "scx");
		properties.put("spring.datasource.dynamic.datasource.master.password", "synthetic-secret");
		properties.put("spring.data.redis.host", "redis");
		properties.put("spring.data.redis.password", "synthetic-secret");
		properties.put("rocketmq.name-server", "mq:9876");
		properties.put("xxl.job.adminAddresses", "https://xxl.example.invalid");
		properties.put("xxl.job.accessToken", "synthetic-secret");
		properties.put("encode.key", "synthetic-secret");
		properties.put("scx.tracking.channel-master-key", "synthetic-secret");
		properties.put("scx.tracking.device-hmac-key", "synthetic-secret");
		return properties;
	}

	private static String environmentVariableFor(String property) {
		return switch (property) {
			case "spring.datasource.dynamic.datasource.master.url" -> "SCX_DATASOURCE_URL";
			case "spring.datasource.dynamic.datasource.master.username" -> "SCX_DATASOURCE_USERNAME";
			case "spring.datasource.dynamic.datasource.master.password" -> "SCX_DATASOURCE_PASSWORD";
			case "spring.data.redis.host" -> "SCX_REDIS_HOST";
			case "spring.data.redis.password" -> "SCX_REDIS_PASSWORD";
			case "rocketmq.name-server" -> "ROCKETMQ_NAME_SERVER";
			case "xxl.job.adminAddresses" -> "SCX_XXL_JOB_ADMIN_ADDRESSES";
			case "xxl.job.accessToken" -> "SCX_XXL_JOB_ACCESS_TOKEN";
			case "encode.key" -> "SCX_ENCODE_KEY";
			case "scx.tracking.channel-master-key" -> "SCX_TRACKING_CHANNEL_MASTER_KEY";
			case "scx.tracking.device-hmac-key" -> "SCX_TRACKING_DEVICE_HMAC_KEY";
			default -> throw new IllegalArgumentException(property);
		};
	}

}
