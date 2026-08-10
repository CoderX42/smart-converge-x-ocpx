package com.yzytech.scx.adx.config;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AdxOcpxCryptoConfigurationTest {

	@Test
	void createsOnlyTheSharedProtocolCryptoCapability() {
		new ApplicationContextRunner().withPropertyValues("scx.ocpx.payload-aes-key=test-protocol-key")
			.withUserConfiguration(AdxOcpxCryptoConfiguration.class)
			.run(context -> assertThat(context).hasNotFailed()
				.hasSingleBean(ProtocolCrypto.class)
				.doesNotHaveBean(OcpxRuntimeProperties.class));
	}

}
