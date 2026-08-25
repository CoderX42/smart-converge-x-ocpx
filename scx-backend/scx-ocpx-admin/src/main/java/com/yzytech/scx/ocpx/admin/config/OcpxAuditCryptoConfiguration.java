package com.yzytech.scx.ocpx.admin.config;

import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(OcpxAuditCryptoConfiguration.Properties.class)
public class OcpxAuditCryptoConfiguration {

	@Bean
	public OcpxKeyRingCrypto ocpxAdminKeyRingCrypto(Properties properties) {
		return new OcpxKeyRingCrypto(properties.getActiveKeyId(), properties.getKeys(), properties.getLegacyOrder());
	}

	@Data
	@ConfigurationProperties("app.callback-crypto")
	public static class Properties {

		private String activeKeyId;

		private Map<String, String> keys = new LinkedHashMap<>();

		private List<String> legacyOrder = new ArrayList<>();

	}

}
