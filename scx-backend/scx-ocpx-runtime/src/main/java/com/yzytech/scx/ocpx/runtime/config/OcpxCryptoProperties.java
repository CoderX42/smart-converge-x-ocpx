package com.yzytech.scx.ocpx.runtime.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "scx.ocpx")
public class OcpxCryptoProperties {

	@NotBlank
	private String payloadAesKey;

	public String getPayloadAesKey() {
		return payloadAesKey;
	}

	public void setPayloadAesKey(String payloadAesKey) {
		this.payloadAesKey = payloadAesKey;
	}

}
