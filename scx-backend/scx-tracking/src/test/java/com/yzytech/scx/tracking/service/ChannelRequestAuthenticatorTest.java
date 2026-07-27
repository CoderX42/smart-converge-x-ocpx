package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.entity.ChannelClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ChannelRequestAuthenticatorTest {

	private final ChannelRequestAuthenticator authenticator = new ChannelRequestAuthenticator(new TrackingProperties(),
			mock(ChannelCredentialCache.class), mock(ObjectProvider.class));

	@Test
	void enforcesEndpointScopeAndAllowsReceiptsForEventClients() {
		ChannelClient client = new ChannelClient();
		client.setAllowedScopes("CLICK");

		assertThatCode(() -> authenticator.requireScope(client, "/open-api/events/v1/clicks"))
			.doesNotThrowAnyException();
		assertThatCode(() -> authenticator.requireScope(client, "/open-api/events/v1/receipts/receipt-1"))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> authenticator.requireScope(client, "/open-api/events/v1/conversions"))
			.isInstanceOfSatisfying(ChannelAuthenticationException.class,
					exception -> assertThat(exception.status()).isEqualTo(403));
		assertThatThrownBy(() -> authenticator.requireScope(client, "/open-api/rta/v1/decisions"))
			.isInstanceOf(ChannelAuthenticationException.class)
			.hasMessage("渠道凭据无权调用该接口");
	}

	@Test
	void rejectsUnknownMachineEndpoint() {
		ChannelClient client = new ChannelClient();
		client.setAllowedScopes("CLICK,CONVERSION,RTA");

		assertThatThrownBy(() -> authenticator.requireScope(client, "/open-api/unknown"))
			.isInstanceOf(ChannelAuthenticationException.class)
			.hasMessage("渠道凭据无权调用该接口");
	}

}
