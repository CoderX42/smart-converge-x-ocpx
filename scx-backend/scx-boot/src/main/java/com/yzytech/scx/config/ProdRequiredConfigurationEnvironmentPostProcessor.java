package com.yzytech.scx.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Fails production startup before infrastructure beans are created when a required
 * external connection or secret is unresolved.
 */
public class ProdRequiredConfigurationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final Map<String, String> REQUIRED_PROPERTIES = requiredProperties();

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (!environment.acceptsProfiles(Profiles.of("prod"))) {
			return;
		}
		List<String> missing = new ArrayList<>();
		REQUIRED_PROPERTIES.forEach((property, environmentVariable) -> {
			if (!hasResolvedText(environment, property)) {
				missing.add(environmentVariable);
			}
		});
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Missing required production configuration: " + String.join(", ", missing));
		}
	}

	@Override
	public int getOrder() {
		// Run after ConfigDataEnvironmentPostProcessor has loaded application-prod.yml,
		// but before the application context and infrastructure beans are created.
		return Ordered.LOWEST_PRECEDENCE;
	}

	private static boolean hasResolvedText(ConfigurableEnvironment environment, String property) {
		try {
			String value = environment.getProperty(property);
			return StringUtils.hasText(value) && !value.contains("${");
		}
		catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static Map<String, String> requiredProperties() {
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put("spring.datasource.dynamic.datasource.master.url", "SCX_DATASOURCE_URL");
		properties.put("spring.datasource.dynamic.datasource.master.username", "SCX_DATASOURCE_USERNAME");
		properties.put("spring.datasource.dynamic.datasource.master.password", "SCX_DATASOURCE_PASSWORD");
		properties.put("spring.data.redis.host", "SCX_REDIS_HOST");
		properties.put("spring.data.redis.password", "SCX_REDIS_PASSWORD");
		properties.put("rocketmq.name-server", "ROCKETMQ_NAME_SERVER");
		properties.put("xxl.job.adminAddresses", "SCX_XXL_JOB_ADMIN_ADDRESSES");
		properties.put("xxl.job.accessToken", "SCX_XXL_JOB_ACCESS_TOKEN");
		properties.put("encode.key", "SCX_ENCODE_KEY");
		properties.put("scx.tracking.channel-master-key", "SCX_TRACKING_CHANNEL_MASTER_KEY");
		properties.put("scx.tracking.device-hmac-key", "SCX_TRACKING_DEVICE_HMAC_KEY");
		return Map.copyOf(properties);
	}

}
