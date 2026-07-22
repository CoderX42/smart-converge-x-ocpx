package com.yzytech.scx.tracking.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedbackStoreSafety {

	private final TrackingProperties properties;

	private final Environment environment;

	@PostConstruct
	void validate() {
		if (environment.acceptsProfiles(Profiles.of("prod", "production"))
				&& !"redis".equalsIgnoreCase(properties.getFeedbackStore())) {
			throw new IllegalStateException("生产环境必须配置 scx.tracking.feedback-store=redis");
		}
	}

}
