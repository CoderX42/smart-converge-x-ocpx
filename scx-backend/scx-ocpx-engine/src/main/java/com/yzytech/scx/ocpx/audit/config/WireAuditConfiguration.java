package com.yzytech.scx.ocpx.audit.config;

import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ CallbackCryptoProperties.class, WireAuditProperties.class })
public class WireAuditConfiguration {

	@Bean
	public OcpxKeyRingCrypto ocpxKeyRingCrypto(CallbackCryptoProperties properties) {
		return new OcpxKeyRingCrypto(properties.getActiveKeyId(), properties.getKeys(), properties.getLegacyOrder());
	}

}
