package com.yzytech.scx.tracking.config;

import lombok.Data;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "scx.tracking")
public class TrackingProperties {

	private boolean sandboxEnabled;

	private String sandboxSigningKey;

	private String attributionRuleVersion = "last-click-v1";

	private String callbackSandboxEndpoint = "http://callback-mock:8080/callback";

	private List<String> callbackAllowedHosts = List.of("localhost", "127.0.0.1", "callback-mock");

	private boolean outboxPublisherEnabled;

	/**
	 * Allows an operator to pause only the scheduled outbox scan during a controlled
	 * recovery drill. Targeted, authorized publication remains available.
	 */
	private boolean outboxSchedulerEnabled = true;

	private String outboxTopic = "scx_tracking_events";

}
