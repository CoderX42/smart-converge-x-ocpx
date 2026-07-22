package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.tracking.entity.ChannelCredential;
import com.yzytech.scx.tracking.mapper.ChannelClientMapper;
import com.yzytech.scx.tracking.mapper.ChannelCredentialMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class ChannelCredentialCacheTest {

	@Test
	void localValueExpiresImmediatelyAfterBroadcastInvalidation() {
		ChannelClientMapper clients = mock(ChannelClientMapper.class);
		ChannelCredentialMapper credentials = mock(ChannelCredentialMapper.class);
		ChannelCredentialCrypto crypto = mock(ChannelCredentialCrypto.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<RedisMessageListenerContainer> listeners = mock(ObjectProvider.class);
		ChannelClient client = new ChannelClient();
		client.setId("channel-1");
		client.setClientId("scx_client");
		client.setCurrentKeyVersion("v1");
		ChannelCredential credential = new ChannelCredential();
		credential.setEncryptedSecret("cipher");
		credential.setSecretIv("iv");
		when(clients.selectActiveByClientId("scx_client")).thenReturn(client);
		when(credentials.selectActive("channel-1", "v1")).thenReturn(credential);
		when(crypto.decrypt("cipher", "iv")).thenReturn("secret");
		ChannelCredentialCache cache = new ChannelCredentialCache(clients, credentials, crypto, redis, listeners);

		assertThat(cache.resolve("scx_client", "v1").secret()).isEqualTo("secret");
		assertThat(cache.resolve("scx_client", "v1").secret()).isEqualTo("secret");
		Message message = mock(Message.class);
		when(message.getBody()).thenReturn("scx_client".getBytes(StandardCharsets.UTF_8));
		cache.onMessage(message, null);
		assertThat(cache.resolve("scx_client", "v1").secret()).isEqualTo("secret");

		verify(clients, times(2)).selectActiveByClientId("scx_client");
		verify(credentials, times(2)).selectActive("channel-1", "v1");
	}

}
