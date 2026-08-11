package com.yzytech.scx.ocpx.admin.config;

import com.yzytech.scx.ocpx.runtime.config.OcpxCryptoProperties;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OcpxCryptoProperties.class)
public class OcpxAdminCryptoConfiguration {

	@Bean
	ProtocolCrypto protocolCrypto(OcpxCryptoProperties properties) {
		return new ProtocolCrypto(properties);
	}

}
