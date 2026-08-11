package com.yzytech.scx.ocpx.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "scx.api-debug")
public class ApiDebugProperties {

	private boolean enabled = true;

	private boolean fullChainEnabled = true;

	private boolean rawLogsEnabled = true;

	private String environment = "development";

	private String engineBaseUrl = "http://127.0.0.1:10000/ocpx";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isFullChainEnabled() {
		return fullChainEnabled;
	}

	public void setFullChainEnabled(boolean fullChainEnabled) {
		this.fullChainEnabled = fullChainEnabled;
	}

	public boolean isRawLogsEnabled() {
		return rawLogsEnabled;
	}

	public void setRawLogsEnabled(boolean rawLogsEnabled) {
		this.rawLogsEnabled = rawLogsEnabled;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public String getEngineBaseUrl() {
		return engineBaseUrl;
	}

	public void setEngineBaseUrl(String engineBaseUrl) {
		this.engineBaseUrl = engineBaseUrl;
	}

}
