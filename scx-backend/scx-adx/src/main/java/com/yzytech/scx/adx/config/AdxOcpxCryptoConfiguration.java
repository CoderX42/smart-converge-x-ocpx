package com.yzytech.scx.adx.config;

import com.yzytech.scx.ocpx.runtime.config.OcpxCryptoProperties;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OcpxCryptoProperties.class)
public class AdxOcpxCryptoConfiguration {

	@Bean
	ProtocolCrypto protocolCrypto(OcpxCryptoProperties properties) {
		return new ProtocolCrypto(properties);
	}

}
