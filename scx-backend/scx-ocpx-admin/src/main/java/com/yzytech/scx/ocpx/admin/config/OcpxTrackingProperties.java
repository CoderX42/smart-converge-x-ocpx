package com.yzytech.scx.ocpx.admin.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "scx.ocpx.admin.tracking")
public class OcpxTrackingProperties {

	@NotBlank
	private String engineBaseUrl = "https://tracking.invalid";

	public String getEngineBaseUrl() {
		return engineBaseUrl;
	}

	public void setEngineBaseUrl(String engineBaseUrl) {
		this.engineBaseUrl = engineBaseUrl;
	}

}
