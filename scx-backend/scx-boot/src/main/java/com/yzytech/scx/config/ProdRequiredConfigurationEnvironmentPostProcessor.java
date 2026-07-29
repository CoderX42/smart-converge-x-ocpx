package com.yzytech.scx.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Production configuration is maintained directly in {@code application-prod.yml}.
 * Environment-variable injection and fail-closed missing-env checks are disabled
 * so Baota / plain {@code java -jar} can start without {@code /etc/scx/scx.env}.
 */
public class ProdRequiredConfigurationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		// no-op: prod values come from application-prod.yml
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
