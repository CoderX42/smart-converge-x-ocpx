package com.yzytech.scx.ocpx.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "scx.ocpx.partner-credentials")
public class PartnerCredentialProperties {

	private Map<String, Credential> advertisers = new LinkedHashMap<>();

	private Map<String, Credential> media = new LinkedHashMap<>();

	public Map<String, Credential> getAdvertisers() {
		return advertisers;
	}

	public void setAdvertisers(Map<String, Credential> advertisers) {
		this.advertisers = advertisers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(advertisers);
	}

	public Map<String, Credential> getMedia() {
		return media;
	}

	public void setMedia(Map<String, Credential> media) {
		this.media = media == null ? new LinkedHashMap<>() : new LinkedHashMap<>(media);
	}

	public static class Credential {

		private long version;

		private String account;

		private String secret;

		public long getVersion() {
			return version;
		}

		public void setVersion(long version) {
			this.version = version;
		}

		public String getAccount() {
			return account;
		}

		public void setAccount(String account) {
			this.account = account;
		}

		public String getSecret() {
			return secret;
		}

		public void setSecret(String secret) {
			this.secret = secret;
		}
	}

}
