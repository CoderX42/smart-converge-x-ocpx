package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.dto.ChannelClientCreateRequest;
import com.yzytech.scx.tracking.dto.ChannelClientUpdateRequest;
import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.tracking.entity.ChannelCredential;
import com.yzytech.scx.tracking.mapper.ChannelClientMapper;
import com.yzytech.scx.tracking.mapper.ChannelCredentialMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChannelClientManagementServiceTest {

	private final ChannelClientMapper clientMapper = mock(ChannelClientMapper.class);

	private final ChannelCredentialMapper credentialMapper = mock(ChannelCredentialMapper.class);

	private final ChannelCredentialCrypto crypto = mock(ChannelCredentialCrypto.class);

	private final ChannelCredentialCache cache = mock(ChannelCredentialCache.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final ChannelClientManagementService service = new ChannelClientManagementService(clientMapper,
			credentialMapper, crypto, cache, scopeService);

	@BeforeEach
	void setUp() {
		when(scopeService.canRead("media-1")).thenReturn(true);
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(crypto.encrypt(any())).thenReturn(new ChannelCredentialCrypto.EncryptedValue("cipher", "iv"));
		doAnswer(invocation -> {
			ChannelClient value = invocation.getArgument(0);
			value.setId("channel-1");
			return 1;
		}).when(clientMapper).insert(any(ChannelClient.class));
	}

	@Test
	void createReturnsSecretOnceAndPersistsOnlyEncryptedCredential() {
		ChannelClientCreateRequest request = new ChannelClientCreateRequest();
		request.setMediaId("media-1");
		request.setSourceCode("partner_one");
		request.setDynamicCallbackEnabled(true);
		request.setCallbackAllowedHosts(List.of("Callback.Example.COM"));

		var response = service.create(request);

		assertThat(response.clientId()).startsWith("scx_");
		assertThat(response.keyVersion()).isEqualTo("v1");
		assertThat(response.secret()).hasSize(43);
		ArgumentCaptor<ChannelCredential> credential = ArgumentCaptor.forClass(ChannelCredential.class);
		verify(credentialMapper).insert(credential.capture());
		assertThat(credential.getValue().getEncryptedSecret()).isEqualTo("cipher");
		assertThat(credential.getValue().getEncryptedSecret()).isNotEqualTo(response.secret());
		verify(cache).evictAndPublish(response.clientId());
	}

	@Test
	void dynamicCallbackRequiresExactPublicDnsHost() {
		ChannelClientCreateRequest request = new ChannelClientCreateRequest();
		request.setMediaId("media-1");
		request.setSourceCode("partner");
		request.setDynamicCallbackEnabled(true);
		request.setCallbackAllowedHosts(List.of("127.0.0.1"));

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("公网 DNS");
	}

	@Test
	void rotateKeepsOldVersionForTwentyFourHours() {
		ChannelClient client = activeClient();
		ChannelCredential current = new ChannelCredential();
		current.setId("credential-1");
		current.setChannelClientId(client.getId());
		current.setKeyVersion("v1");
		current.setStatus("ACTIVE");
		when(clientMapper.selectByIdForUpdate("channel-1")).thenReturn(client);
		when(credentialMapper.selectOne(any())).thenReturn(current);

		var response = service.rotate("channel-1", "media-1");

		assertThat(response.keyVersion()).isEqualTo("v2");
		assertThat(response.previousKeyValidUntil()).isAfter(response.issuedAt().plusHours(23));
		assertThat(current.getValidUntil()).isEqualTo(response.previousKeyValidUntil());
		assertThat(client.getCurrentKeyVersion()).isEqualTo("v2");
	}

	@Test
	void currentVersionCannotBeRevoked() {
		ChannelClient client = activeClient();
		when(clientMapper.selectByIdForUpdate("channel-1")).thenReturn(client);

		assertThatThrownBy(() -> service.revoke("channel-1", "v1", "media-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("请先轮换");
	}

	@Test
	void updateDoesNotAllowCrossMediaAccess() {
		when(scopeService.canWrite("media-2")).thenReturn(false);
		ChannelClientUpdateRequest request = new ChannelClientUpdateRequest();
		request.setMediaId("media-2");
		request.setMaxQps(100);
		request.setStatus("ACTIVE");

		assertThatThrownBy(() -> service.update("channel-1", request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("资源不存在");
	}

	private ChannelClient activeClient() {
		ChannelClient client = new ChannelClient();
		client.setId("channel-1");
		client.setMediaId("media-1");
		client.setClientId("scx_client");
		client.setStatus("ACTIVE");
		client.setCurrentKeyVersion("v1");
		return client;
	}

}
