package com.yzytech.scx.ocpx.runtime.config;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class PartnerCredentialResolver {

	private final PartnerCredentialProperties properties;

	public PartnerCredentialResolver(PartnerCredentialProperties properties) {
		this.properties = properties;
	}

	public ResolvedCredential requireAdvertiser(String configKey, long version) {
		return require("advertiser", properties.getAdvertisers(), configKey, version);
	}

	public ResolvedCredential requireMedia(String authType, String configKey, long version) {
		if ("NONE".equals(authType)) {
			return ResolvedCredential.none();
		}
		return require("media", properties.getMedia(), configKey, version);
	}

	private ResolvedCredential require(String kind, Map<String, PartnerCredentialProperties.Credential> values,
			String configKey, long version) {
		if (!StringUtils.hasText(configKey)) {
			throw new IllegalStateException(kind + " credential config key is missing");
		}
		PartnerCredentialProperties.Credential credential = values.get(configKey);
		if (credential == null) {
			throw new IllegalStateException(kind + " credential config key is not configured");
		}
		if (credential.getVersion() != version) {
			throw new IllegalStateException(kind + " credential version mismatch");
		}
		if (!StringUtils.hasText(credential.getAccount()) || !StringUtils.hasText(credential.getSecret())) {
			throw new IllegalStateException(kind + " credential is incomplete");
		}
		return new ResolvedCredential(credential.getVersion(), credential.getAccount(), credential.getSecret());
	}

	public record ResolvedCredential(long version, String account, String secret) {
		static ResolvedCredential none() {
			return new ResolvedCredential(0L, null, null);
		}
	}

}
