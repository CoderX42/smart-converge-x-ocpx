package com.yzytech.scx.tracking.service;

import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.entity.ChannelClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChannelRequestAuthenticator {

	private static final Logger log = LoggerFactory.getLogger(ChannelRequestAuthenticator.class);

	private final TrackingProperties properties;

	private final ChannelCredentialCache credentialCache;

	private final ObjectProvider<StringRedisTemplate> redisProvider;

	private final Map<String, Long> localNonces = new ConcurrentHashMap<>();

	private final Map<String, LocalRateWindow> localRates = new ConcurrentHashMap<>();

	public ChannelAuthContext authenticate(String clientId, String keyVersion, String timestamp, String nonce,
			String sign, String method, String path, String canonicalPayload) {
		if (!StringUtils.hasText(clientId) || !StringUtils.hasText(keyVersion) || !StringUtils.hasText(timestamp)
				|| !StringUtils.hasText(nonce) || !StringUtils.hasText(sign)) {
			throw new ChannelAuthenticationException(401, "缺少渠道认证头");
		}
		if (nonce.length() < 16 || nonce.length() > 128)
			throw new ChannelAuthenticationException(401, "nonce 长度无效");
		long requestTime;
		try {
			requestTime = Long.parseLong(timestamp);
		}
		catch (NumberFormatException exception) {
			throw new ChannelAuthenticationException(401, "时间戳无效");
		}
		if (Math.abs(System.currentTimeMillis() - requestTime) > properties.getMachineClockSkewMs())
			throw new ChannelAuthenticationException(401, "请求时间超出允许范围");

		ChannelCredentialCache.CachedCredential cached;
		try {
			cached = credentialCache.resolve(clientId, keyVersion);
		}
		catch (RuntimeException exception) {
			throw new ChannelAuthenticationException(503, "渠道认证服务不可用");
		}
		if (cached == null)
			throw new ChannelAuthenticationException(401, "渠道凭据无效");
		ChannelClient client = cached.client();
		String secret = cached.secret();
		String signingInput = method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n"
				+ SignUtils.sha256(canonicalPayload);
		String expected = SignUtils.hmacSha256(signingInput, secret);
		if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
				sign.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
			log.debug("Channel signature mismatch: clientId={}, keyVersion={}, payloadHash={}", clientId, keyVersion,
					SignUtils.sha256(canonicalPayload));
			throw new ChannelAuthenticationException(401, "渠道签名无效");
		}
		claimNonce(clientId, keyVersion, nonce);
		checkRate(client, path);
		return new ChannelAuthContext(client, keyVersion, secret);
	}

	private void claimNonce(String clientId, String keyVersion, String nonce) {
		String key = "scx:ocpx:nonce:" + clientId + ":" + keyVersion + ":" + SignUtils.sha256(nonce);
		StringRedisTemplate redis = redisProvider.getIfAvailable();
		if (redis != null) {
			try {
				Boolean claimed = redis.opsForValue()
					.setIfAbsent(key, "1", Duration.ofSeconds(properties.getNonceTtlSeconds()));
				if (!Boolean.TRUE.equals(claimed))
					throw new ChannelAuthenticationException(401, "nonce 已使用");
				return;
			}
			catch (ChannelAuthenticationException exception) {
				throw exception;
			}
			catch (RuntimeException exception) {
				throw new ChannelAuthenticationException(503, "nonce 存储不可用");
			}
		}
		if (!"memory".equalsIgnoreCase(properties.getFeedbackStore()))
			throw new ChannelAuthenticationException(503, "nonce 存储不可用");
		long now = System.currentTimeMillis();
		localNonces.entrySet().removeIf(entry -> entry.getValue() <= now);
		if (localNonces.putIfAbsent(key, now + properties.getNonceTtlSeconds() * 1000) != null)
			throw new ChannelAuthenticationException(401, "nonce 已使用");
	}

	private void checkRate(ChannelClient client, String path) {
		int maxQps = client.getMaxQps() == null || client.getMaxQps() < 1 ? 1 : client.getMaxQps();
		long epochSecond = System.currentTimeMillis() / 1000;
		String route = SignUtils.sha256(path).substring(0, 12);
		String key = "scx:ocpx:rate:" + client.getClientId() + ":" + route + ":" + epochSecond;
		StringRedisTemplate redis = redisProvider.getIfAvailable();
		if (redis != null) {
			try {
				Long count = redis.opsForValue().increment(key);
				if (count != null && count == 1)
					redis.expire(key, Duration.ofSeconds(2));
				if (count != null && count > maxQps)
					throw new ChannelAuthenticationException(429, "渠道请求超过配额");
				return;
			}
			catch (ChannelAuthenticationException exception) {
				throw exception;
			}
			catch (RuntimeException exception) {
				throw new ChannelAuthenticationException(503, "限流服务不可用");
			}
		}
		if (!"memory".equalsIgnoreCase(properties.getFeedbackStore()))
			throw new ChannelAuthenticationException(503, "限流服务不可用");
		LocalRateWindow window = localRates.compute(key,
				(ignored, existing) -> existing == null ? new LocalRateWindow(epochSecond)
						: existing.second == epochSecond ? existing : new LocalRateWindow(epochSecond));
		if (window.count.incrementAndGet() > maxQps)
			throw new ChannelAuthenticationException(429, "渠道请求超过配额");
		localRates.entrySet().removeIf(entry -> entry.getValue().second < epochSecond - 2);
	}

	private static final class LocalRateWindow {

		private final long second;

		private final AtomicInteger count = new AtomicInteger();

		private LocalRateWindow(long second) {
			this.second = second;
		}

	}

}
