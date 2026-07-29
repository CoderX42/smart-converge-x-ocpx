package com.yzytech.scx.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ProdRequiredConfigurationEnvironmentPostProcessorTest {

	private final ProdRequiredConfigurationEnvironmentPostProcessor processor = new ProdRequiredConfigurationEnvironmentPostProcessor();

	@Test
	void doesNotRequireEnvironmentVariablesForProd() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");

		assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
			.doesNotThrowAnyException();
	}

	@Test
	void ignoresNonProductionProfiles() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("dev");

		assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
			.doesNotThrowAnyException();
	}

}
