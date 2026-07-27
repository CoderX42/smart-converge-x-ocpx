package com.yzytech.scx.tracking.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "scx.tracking")
public class TrackingProperties {

	private boolean sandboxEnabled;

	private String sandboxSigningKey;

	/** Base64 encoded AES-256 key used only to encrypt channel credentials and URLs. */
	@NotBlank(message = "scx.tracking.channel-master-key must not be blank")
	private String channelMasterKey;

	/** Base64 encoded HMAC key used to derive non-exportable internal device keys. */
	@NotBlank(message = "scx.tracking.device-hmac-key must not be blank")
	private String deviceHmacKey;

	private String deviceKeyVersion = "v1";

	private boolean imeiEnabled;

	private long machineClockSkewMs = 180_000;

	private long nonceTtlSeconds = 300;

	private String feedbackStore = "memory";

	private int feedbackQueueCapacity = 10_000;

	private int feedbackStreamPartitions = 64;

	private int feedbackBatchSize = 200;

	private long feedbackIdempotencyTtlSeconds = 604_800;

	private long feedbackPendingIdleMs = 30_000;

	private int feedbackMaxDeliveries = 5;

	private boolean ingressEnabled = true;

	private boolean workerEnabled = true;

	private int workerBatchSize = 200;

	private String attributionRuleVersion = "last-click-v1";

	private String callbackSandboxEndpoint = "http://callback-mock:8080/callback";

	private List<String> callbackAllowedHosts = List.of("localhost", "127.0.0.1", "callback-mock");

	private int callbackConnectTimeoutMs = 1_000;

	private int callbackReadTimeoutMs = 3_000;

	private int workerLeaseSeconds = 30;

	private long rtaReservationTtlSeconds = 10;

	private long rtaResultTtlSeconds = 86_400;

	private boolean outboxPublisherEnabled;

	/**
	 * Allows an operator to pause only the scheduled outbox scan during a controlled
	 * recovery drill. Targeted, authorized publication remains available.
	 */
	private boolean outboxSchedulerEnabled = true;

	private String outboxTopic = "scx_tracking_events";

}
