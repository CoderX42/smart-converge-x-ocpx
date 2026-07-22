package com.yzytech.scx.tracking.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class FeedbackStoreSafetyTest {

	@Test
	void rejectsMemoryBufferInProduction() {
		TrackingProperties properties = new TrackingProperties();
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("production");

		assertThatThrownBy(() -> new FeedbackStoreSafety(properties, environment).validate())
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("生产环境必须配置 scx.tracking.feedback-store=redis");
	}

	@Test
	void allowsRedisInProductionAndMemoryLocally() {
		TrackingProperties properties = new TrackingProperties();
		MockEnvironment production = new MockEnvironment();
		production.setActiveProfiles("prod");
		properties.setFeedbackStore("redis");
		assertThatCode(() -> new FeedbackStoreSafety(properties, production).validate()).doesNotThrowAnyException();

		properties.setFeedbackStore("memory");
		assertThatCode(() -> new FeedbackStoreSafety(properties, new MockEnvironment()).validate())
			.doesNotThrowAnyException();
	}

}
