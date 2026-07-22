package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.tracking.entity.ChannelCredential;
import com.yzytech.scx.tracking.mapper.ChannelClientMapper;
import com.yzytech.scx.tracking.mapper.ChannelCredentialMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChannelCredentialCache implements MessageListener {

	private static final String INVALIDATION_TOPIC = "scx:ocpx:channel-cache:invalidate";

	private static final long TTL_MILLIS = Duration.ofSeconds(60).toMillis();

	private final ChannelClientMapper clientMapper;

	private final ChannelCredentialMapper credentialMapper;

	private final ChannelCredentialCrypto crypto;

	private final ObjectProvider<StringRedisTemplate> redisProvider;

	private final ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider;

	private final Map<String, CachedCredential> values = new ConcurrentHashMap<>();

	@PostConstruct
	void subscribe() {
		RedisMessageListenerContainer container = listenerContainerProvider.getIfAvailable();
		if (container != null)
			container.addMessageListener(this, new ChannelTopic(INVALIDATION_TOPIC));
	}

	public CachedCredential resolve(String clientId, String keyVersion) {
		String cacheKey = clientId + ':' + keyVersion;
		long now = System.currentTimeMillis();
		CachedCredential cached = values.get(cacheKey);
		if (cached != null && cached.expiresAt() > now)
			return cached;
		if (cached != null)
			values.remove(cacheKey, cached);
		ChannelClient client = clientMapper.selectActiveByClientId(clientId);
		if (client == null)
			return null;
		ChannelCredential credential = credentialMapper.selectActive(client.getId(), keyVersion);
		if (credential == null)
			return null;
		CachedCredential loaded = new CachedCredential(client, keyVersion,
				crypto.decrypt(credential.getEncryptedSecret(), credential.getSecretIv()), now + TTL_MILLIS);
		values.put(cacheKey, loaded);
		return loaded;
	}

	public CachedCredential resolveCurrent(String clientId) {
		ChannelClient client = clientMapper.selectActiveByClientId(clientId);
		if (client == null)
			return null;
		return resolve(clientId, client.getCurrentKeyVersion());
	}

	public void evictAndPublish(String clientId) {
		evict(clientId);
		StringRedisTemplate redis = redisProvider.getIfAvailable();
		if (redis != null) {
			try {
				redis.convertAndSend(INVALIDATION_TOPIC, clientId);
			}
			catch (RuntimeException ignored) {
				// Production ingress remains fail-closed through Redis-backed nonce/rate
				// checks.
			}
		}
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		evict(new String(message.getBody(), StandardCharsets.UTF_8));
	}

	private void evict(String clientId) {
		values.keySet().removeIf(key -> key.startsWith(clientId + ':'));
	}

	public record CachedCredential(ChannelClient client, String keyVersion, String secret, long expiresAt) {
	}

}
