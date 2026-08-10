package com.yzytech.scx.adx.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "scx.adx.tracking")
public class AdxTrackingProperties {

	@NotBlank
	private String publicBaseUrl = "https://tracking.invalid";

	@Min(30)
	@Max(600)
	private int publishCheckTtlSeconds = 120;

	@Min(0)
	@Max(86400)
	private int addressOverlapSeconds = 300;

	@Min(1)
	@Max(100)
	private int minimumRequiredAcks = 1;

	private boolean distributionWorkerEnabled;

	public String getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(String publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}

	public int getPublishCheckTtlSeconds() {
		return publishCheckTtlSeconds;
	}

	public void setPublishCheckTtlSeconds(int publishCheckTtlSeconds) {
		this.publishCheckTtlSeconds = publishCheckTtlSeconds;
	}

	public int getAddressOverlapSeconds() {
		return addressOverlapSeconds;
	}

	public void setAddressOverlapSeconds(int addressOverlapSeconds) {
		this.addressOverlapSeconds = addressOverlapSeconds;
	}

	public int getMinimumRequiredAcks() {
		return minimumRequiredAcks;
	}

	public void setMinimumRequiredAcks(int minimumRequiredAcks) {
		this.minimumRequiredAcks = minimumRequiredAcks;
	}

	public boolean isDistributionWorkerEnabled() {
		return distributionWorkerEnabled;
	}

	public void setDistributionWorkerEnabled(boolean distributionWorkerEnabled) {
		this.distributionWorkerEnabled = distributionWorkerEnabled;
	}

}
