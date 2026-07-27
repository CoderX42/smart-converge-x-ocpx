package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.dto.ChannelClientCreateRequest;
import com.yzytech.scx.tracking.dto.ChannelClientUpdateRequest;
import com.yzytech.scx.tracking.dto.ChannelCredentialIssueResponse;
import com.yzytech.scx.tracking.dto.ChannelCredentialView;
import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.tracking.entity.ChannelCredential;
import com.yzytech.scx.tracking.mapper.ChannelClientMapper;
import com.yzytech.scx.tracking.mapper.ChannelCredentialMapper;
import java.net.IDN;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChannelClientManagementService {

	private static final Pattern SOURCE_CODE = Pattern.compile("[A-Za-z0-9._-]{2,64}");

	private static final Pattern KEY_VERSION = Pattern.compile("v([1-9][0-9]*)");

	private static final Pattern DNS_HOST = Pattern
		.compile("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

	private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

	private static final List<String> ENDPOINT_SCOPES = List.of("CLICK", "CONVERSION", "RTA");

	private final ChannelClientMapper clientMapper;

	private final ChannelCredentialMapper credentialMapper;

	private final ChannelCredentialCrypto crypto;

	private final ChannelCredentialCache credentialCache;

	private final MediaScopeService scopeService;

	private final SecureRandom secureRandom = new SecureRandom();

	public Page<ChannelClient> page(Page<ChannelClient> page, String mediaId, String keyword, String status) {
		requireRead(mediaId);
		return clientMapper.selectPage(page,
				Wrappers.<ChannelClient>lambdaQuery()
					.eq(ChannelClient::getMediaId, mediaId)
					.eq(StringUtils.hasText(status), ChannelClient::getStatus, status)
					.and(StringUtils.hasText(keyword),
							query -> query.like(ChannelClient::getClientId, keyword)
								.or()
								.like(ChannelClient::getSourceCode, keyword))
					.orderByDesc(ChannelClient::getUpdateTime));
	}

	public List<ChannelCredentialView> credentials(String id, String mediaId) {
		ChannelClient client = requireClient(id, mediaId, false);
		return credentialMapper
			.selectList(Wrappers.<ChannelCredential>lambdaQuery()
				.eq(ChannelCredential::getChannelClientId, client.getId())
				.orderByDesc(ChannelCredential::getCreateTime))
			.stream()
			.map(value -> new ChannelCredentialView(value.getKeyVersion(), value.getStatus(), value.getValidFrom(),
					value.getValidUntil(), value.getCreateTime(),
					value.getKeyVersion().equals(client.getCurrentKeyVersion())))
			.toList();
	}

	@Transactional
	public ChannelCredentialIssueResponse create(ChannelClientCreateRequest request) {
		requireWrite(request.getMediaId());
		String sourceCode = normalizeSourceCode(request.getSourceCode());
		if (clientMapper.selectByMediaAndSource(request.getMediaId(), sourceCode) != null)
			throw new IllegalArgumentException("同一媒体下的渠道来源编码已存在");
		String hosts = normalizeHosts(request.getCallbackAllowedHosts(),
				Boolean.TRUE.equals(request.getDynamicCallbackEnabled()));
		String clientId = "scx_" + randomToken(18);
		String secret = randomToken(32);
		LocalDateTime now = credentialMapper.databaseNow();

		ChannelClient client = new ChannelClient();
		client.setMediaId(request.getMediaId());
		client.setClientId(clientId);
		client.setSourceCode(sourceCode);
		client.setAllowedScopes(normalizeScopes(request.getAllowedScopes()));
		client.setStatus("ACTIVE");
		client.setMaxQps(request.getMaxQps() == null ? 2_000 : request.getMaxQps());
		client.setDynamicCallbackEnabled(Boolean.TRUE.equals(request.getDynamicCallbackEnabled()));
		client.setCallbackAllowedHosts(hosts);
		client.setCurrentKeyVersion("v1");
		clientMapper.insert(client);

		insertCredential(client.getId(), "v1", secret, now);
		afterCommit(() -> credentialCache.evictAndPublish(clientId));
		return new ChannelCredentialIssueResponse(client.getId(), clientId, "v1", secret, now, null);
	}

	@Transactional
	public boolean update(String id, ChannelClientUpdateRequest request) {
		ChannelClient client = requireClient(id, request.getMediaId(), true);
		String status = request.getStatus().trim().toUpperCase(Locale.ROOT);
		if (!STATUSES.contains(status))
			throw new IllegalArgumentException("渠道客户端状态无效");
		client.setStatus(status);
		client.setMaxQps(request.getMaxQps());
		if (request.getAllowedScopes() != null)
			client.setAllowedScopes(normalizeScopes(request.getAllowedScopes()));
		client.setDynamicCallbackEnabled(Boolean.TRUE.equals(request.getDynamicCallbackEnabled()));
		client.setCallbackAllowedHosts(
				normalizeHosts(request.getCallbackAllowedHosts(), client.getDynamicCallbackEnabled()));
		boolean changed = clientMapper.updateById(client) == 1;
		afterCommit(() -> credentialCache.evictAndPublish(client.getClientId()));
		return changed;
	}

	@Transactional
	public ChannelCredentialIssueResponse rotate(String id, String mediaId) {
		ChannelClient client = requireClient(id, mediaId, true);
		if (!"ACTIVE".equals(client.getStatus()))
			throw new IllegalArgumentException("请先启用渠道客户端");
		Matcher matcher = KEY_VERSION.matcher(client.getCurrentKeyVersion());
		if (!matcher.matches())
			throw new IllegalStateException("当前密钥版本无法自动轮换");
		String nextVersion = "v" + (Long.parseLong(matcher.group(1)) + 1);
		LocalDateTime now = credentialMapper.databaseNow();
		LocalDateTime oldValidUntil = now.plusHours(24);
		ChannelCredential old = credentialMapper.selectOne(Wrappers.<ChannelCredential>lambdaQuery()
			.eq(ChannelCredential::getChannelClientId, client.getId())
			.eq(ChannelCredential::getKeyVersion, client.getCurrentKeyVersion())
			.last("FOR UPDATE"));
		if (old == null)
			throw new IllegalStateException("当前渠道密钥不存在");
		old.setValidUntil(oldValidUntil);
		credentialMapper.updateById(old);
		String secret = randomToken(32);
		insertCredential(client.getId(), nextVersion, secret, now);
		client.setCurrentKeyVersion(nextVersion);
		clientMapper.updateById(client);
		afterCommit(() -> credentialCache.evictAndPublish(client.getClientId()));
		return new ChannelCredentialIssueResponse(client.getId(), client.getClientId(), nextVersion, secret, now,
				oldValidUntil);
	}

	@Transactional
	public boolean revoke(String id, String keyVersion, String mediaId) {
		ChannelClient client = requireClient(id, mediaId, true);
		if (keyVersion.equals(client.getCurrentKeyVersion()))
			throw new IllegalArgumentException("当前密钥不能直接吊销，请先轮换或停用渠道");
		ChannelCredential credential = credentialMapper.selectOne(Wrappers.<ChannelCredential>lambdaQuery()
			.eq(ChannelCredential::getChannelClientId, client.getId())
			.eq(ChannelCredential::getKeyVersion, keyVersion)
			.last("FOR UPDATE"));
		if (credential == null)
			throw new IllegalArgumentException("密钥版本不存在");
		credential.setStatus("REVOKED");
		credential.setValidUntil(credentialMapper.databaseNow());
		boolean changed = credentialMapper.updateById(credential) == 1;
		afterCommit(() -> credentialCache.evictAndPublish(client.getClientId()));
		return changed;
	}

	private void insertCredential(String channelClientId, String keyVersion, String secret, LocalDateTime now) {
		ChannelCredentialCrypto.EncryptedValue encrypted = crypto.encrypt(secret);
		ChannelCredential credential = new ChannelCredential();
		credential.setChannelClientId(channelClientId);
		credential.setKeyVersion(keyVersion);
		credential.setEncryptedSecret(encrypted.ciphertext());
		credential.setSecretIv(encrypted.iv());
		credential.setStatus("ACTIVE");
		credential.setValidFrom(now);
		credentialMapper.insert(credential);
	}

	private ChannelClient requireClient(String id, String mediaId, boolean forUpdate) {
		if (!StringUtils.hasText(mediaId)
				|| !(forUpdate ? scopeService.canWrite(mediaId) : scopeService.canRead(mediaId)))
			throw new IllegalArgumentException("资源不存在");
		ChannelClient client = forUpdate ? clientMapper.selectByIdForUpdate(id) : clientMapper.selectById(id);
		if (client == null || !mediaId.equals(client.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		return client;
	}

	private String normalizeSourceCode(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if (!SOURCE_CODE.matcher(normalized).matches())
			throw new IllegalArgumentException("渠道来源编码只能包含字母、数字、点、下划线和短横线");
		return normalized;
	}

	private String normalizeHosts(List<String> values, boolean required) {
		List<String> hosts = (values == null ? List.<String>of() : values).stream()
			.map(String::trim)
			.filter(StringUtils::hasText)
			.map(value -> IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT))
			.distinct()
			.toList();
		if (required && hosts.isEmpty())
			throw new IllegalArgumentException("启用动态回调时至少配置一个精确域名");
		if (hosts.size() > 20 || hosts.stream()
			.anyMatch(host -> !DNS_HOST.matcher(host).matches() || host.contains("*") || host.equals("localhost")
					|| host.matches("[0-9.]+")))
			throw new IllegalArgumentException("回调白名单只允许最多 20 个精确公网 DNS 域名");
		return String.join(",", hosts);
	}

	private String normalizeScopes(List<String> values) {
		Set<String> requested = (values == null ? List.<String>of() : values).stream()
			.filter(StringUtils::hasText)
			.map(value -> value.trim().toUpperCase(Locale.ROOT))
			.collect(java.util.stream.Collectors.toSet());
		if (requested.isEmpty() || !ENDPOINT_SCOPES.containsAll(requested))
			throw new IllegalArgumentException("渠道端点权限只能选择 CLICK、CONVERSION 或 RTA");
		return ENDPOINT_SCOPES.stream().filter(requested::contains).collect(java.util.stream.Collectors.joining(","));
	}

	private String randomToken(int bytes) {
		byte[] value = new byte[bytes];
		secureRandom.nextBytes(value);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private void requireRead(String mediaId) {
		if (!StringUtils.hasText(mediaId) || !scopeService.canRead(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

	private void requireWrite(String mediaId) {
		if (!StringUtils.hasText(mediaId) || !scopeService.canWrite(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

	private void afterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

}
