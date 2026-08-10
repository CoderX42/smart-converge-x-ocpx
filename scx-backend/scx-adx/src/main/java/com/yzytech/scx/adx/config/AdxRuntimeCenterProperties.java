package com.yzytech.scx.adx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Literal, fail-closed limits for S10 management queries and replay workers. */
@Data
@Component
@ConfigurationProperties(prefix = "scx.adx.runtime-center")
public class AdxRuntimeCenterProperties {

	private boolean replayWorkerEnabled = false;

	private int defaultSpanHours = 1;

	private int maxSpanHours = 168;

	private int defaultPageSize = 20;

	private int maxPageSize = 200;

	private int previewTtlSeconds = 120;

	private int maxReplayBatch = 100;

	private int maxReplayAttempts = 5;

	private int leaseSeconds = 60;

}
