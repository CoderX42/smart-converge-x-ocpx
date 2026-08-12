package com.yzytech.scx.ocpx.runtime.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "scx.ocpx")
public class OcpxRuntimeProperties {

	@NotBlank
	private String publicBaseUrl;

	private Duration callbackTokenTtl = Duration.ofDays(7);

	private boolean allowHttp;

	private boolean allowPrivateAddresses;

	public String getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(String publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}

	public Duration getCallbackTokenTtl() {
		return callbackTokenTtl;
	}

	public void setCallbackTokenTtl(Duration callbackTokenTtl) {
		this.callbackTokenTtl = callbackTokenTtl;
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

}
