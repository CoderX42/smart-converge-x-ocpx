package com.yzytech.scx.ocpx.runtime.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "scx.ocpx")
public class OcpxRuntimeProperties {

	@NotBlank
	private String publicBaseUrl;

	@Min(1)
	@Max(256)
	private int partitionCount = 64;

	@Min(1)
	@Max(2000)
	private int batchSize = 500;

	@Min(5)
	private long pollDelayMs = 20;

	private Duration idempotencyTtl = Duration.ofDays(7);

	private Duration nonceTtl = Duration.ofMinutes(5);

	private Duration snapshotTtl = Duration.ofDays(7);

	private Duration callbackTokenTtl = Duration.ofDays(7);

	private Duration pendingIdle = Duration.ofSeconds(30);

	@Min(1)
	private int maxDeliveryAttempts = 5;

	@Min(0)
	@Max(2)
	private int replicaAcksRequired;

	@Min(1)
	private long replicaAckTimeoutMs = 20;

	@NotBlank
	private String deviceHmacKey;

	@Min(1)
	private int deviceHmacKeyVersion = 1;

	@NotBlank
	private String callbackTokenKey;

	private boolean allowHttp;

	private boolean allowPrivateAddresses;

	private boolean upstreamDeliveryEnabled;

	private boolean downstreamDeliveryEnabled;

	private boolean debugExecutionEnabled = true;

	@Min(1)
	private int adapterConcurrency = 64;

	@Min(1)
	private int circuitFailureThreshold = 20;

	private Duration circuitOpenDuration = Duration.ofSeconds(30);

	@Min(1)
	private long perChannelQps = 30000;

	public String getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(String publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}

	public int getPartitionCount() {
		return partitionCount;
	}

	public void setPartitionCount(int partitionCount) {
		this.partitionCount = partitionCount;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public long getPollDelayMs() {
		return pollDelayMs;
	}

	public void setPollDelayMs(long pollDelayMs) {
		this.pollDelayMs = pollDelayMs;
	}

	public Duration getIdempotencyTtl() {
		return idempotencyTtl;
	}

	public void setIdempotencyTtl(Duration idempotencyTtl) {
		this.idempotencyTtl = idempotencyTtl;
	}

	public Duration getNonceTtl() {
		return nonceTtl;
	}

	public void setNonceTtl(Duration nonceTtl) {
		this.nonceTtl = nonceTtl;
	}

	public Duration getSnapshotTtl() {
		return snapshotTtl;
	}

	public void setSnapshotTtl(Duration snapshotTtl) {
		this.snapshotTtl = snapshotTtl;
	}

	public Duration getCallbackTokenTtl() {
		return callbackTokenTtl;
	}

	public void setCallbackTokenTtl(Duration callbackTokenTtl) {
		this.callbackTokenTtl = callbackTokenTtl;
	}

	public Duration getPendingIdle() {
		return pendingIdle;
	}

	public void setPendingIdle(Duration pendingIdle) {
		this.pendingIdle = pendingIdle;
	}

	public int getMaxDeliveryAttempts() {
		return maxDeliveryAttempts;
	}

	public void setMaxDeliveryAttempts(int maxDeliveryAttempts) {
		this.maxDeliveryAttempts = maxDeliveryAttempts;
	}

	public int getReplicaAcksRequired() {
		return replicaAcksRequired;
	}

	public void setReplicaAcksRequired(int replicaAcksRequired) {
		this.replicaAcksRequired = replicaAcksRequired;
	}

	public long getReplicaAckTimeoutMs() {
		return replicaAckTimeoutMs;
	}

	public void setReplicaAckTimeoutMs(long replicaAckTimeoutMs) {
		this.replicaAckTimeoutMs = replicaAckTimeoutMs;
	}

	public String getDeviceHmacKey() {
		return deviceHmacKey;
	}

	public void setDeviceHmacKey(String deviceHmacKey) {
		this.deviceHmacKey = deviceHmacKey;
	}

	public int getDeviceHmacKeyVersion() {
		return deviceHmacKeyVersion;
	}

	public void setDeviceHmacKeyVersion(int deviceHmacKeyVersion) {
		this.deviceHmacKeyVersion = deviceHmacKeyVersion;
	}

	public String getCallbackTokenKey() {
		return callbackTokenKey;
	}

	public void setCallbackTokenKey(String callbackTokenKey) {
		this.callbackTokenKey = callbackTokenKey;
	}

	public boolean isAllowHttp() {
		return allowHttp;
	}

	public void setAllowHttp(boolean allowHttp) {
		this.allowHttp = allowHttp;
	}

	public boolean isAllowPrivateAddresses() {
		return allowPrivateAddresses;
	}

	public void setAllowPrivateAddresses(boolean allowPrivateAddresses) {
		this.allowPrivateAddresses = allowPrivateAddresses;
	}

	public boolean isUpstreamDeliveryEnabled() {
		return upstreamDeliveryEnabled;
	}

	public void setUpstreamDeliveryEnabled(boolean upstreamDeliveryEnabled) {
		this.upstreamDeliveryEnabled = upstreamDeliveryEnabled;
	}

	public boolean isDownstreamDeliveryEnabled() {
		return downstreamDeliveryEnabled;
	}

	public void setDownstreamDeliveryEnabled(boolean downstreamDeliveryEnabled) {
		this.downstreamDeliveryEnabled = downstreamDeliveryEnabled;
	}

	public boolean isDebugExecutionEnabled() {
		return debugExecutionEnabled;
	}

	public void setDebugExecutionEnabled(boolean debugExecutionEnabled) {
		this.debugExecutionEnabled = debugExecutionEnabled;
	}

	public int getAdapterConcurrency() {
		return adapterConcurrency;
	}

	public void setAdapterConcurrency(int adapterConcurrency) {
		this.adapterConcurrency = adapterConcurrency;
	}

	public int getCircuitFailureThreshold() {
		return circuitFailureThreshold;
	}

	public void setCircuitFailureThreshold(int circuitFailureThreshold) {
		this.circuitFailureThreshold = circuitFailureThreshold;
	}

	public Duration getCircuitOpenDuration() {
		return circuitOpenDuration;
	}

	public void setCircuitOpenDuration(Duration circuitOpenDuration) {
		this.circuitOpenDuration = circuitOpenDuration;
	}

	public long getPerChannelQps() {
		return perChannelQps;
	}

	public void setPerChannelQps(long perChannelQps) {
		this.perChannelQps = perChannelQps;
	}

}
