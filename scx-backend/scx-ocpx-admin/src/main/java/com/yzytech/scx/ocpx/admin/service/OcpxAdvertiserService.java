package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiserChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiserCredentialRevision;
import com.yzytech.scx.ocpx.admin.entity.OcpxIntegrationDocument;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserCredentialRevisionMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxIntegrationDocumentMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.service.StorageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcpxAdvertiserService {

	private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;

	private static final Set<String> DOCUMENT_TYPES = Set.of("INTEGRATION_GUIDE", "PROTOCOL", "EVENT_MAPPING",
			"OTHER");

	private static final Set<String> CONTENT_TYPES = Set.of("application/pdf", "application/json", "text/plain",
			"application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxAdvertiserCredentialRevisionMapper credentialMapper;

	private final OcpxIntegrationDocumentMapper documentMapper;

	private final OcpxAdvertiserChangeAuditMapper auditMapper;

	private final OcpxMonitorLinkMapper monitorMapper;

	private final OcpxSystemUserMapper systemUserMapper;

	private final OcpxAccessScope accessScope;

	private final OcpxAdvertiserAuthorizationService authorizationService;

	private final OcpxConfigChangeService configChangeService;

	private final OcpxAdapterService adapterService;

	private final ProtocolCrypto protocolCrypto;

	private final StorageUploadService storageUploadService;

	public List<AdvertiserViews.AdapterCapability> adapters() {
		return adapterService.advertiserCapabilities();
	}

	public IPage<AdvertiserViews.ListItem> list(Page<OcpxAdvertiser> page, String keyword, String adapterKey,
			String status, Boolean documentConfigured) {
		String normalizedAdapter = StringUtils.hasText(adapterKey) ? normalizeAdapter(adapterKey) : null;
		OcpxAccessScope.Restriction restriction = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.ADVERTISER, OcpxAccessScope.PermissionLevel.VIEW);
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			Page<AdvertiserViews.ListItem> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
			empty.setRecords(List.of());
			return empty;
		}
		return advertiserMapper.selectPage(page,
				Wrappers.<OcpxAdvertiser>lambdaQuery()
					.in(!restriction.unrestricted(), OcpxAdvertiser::getId, restriction.ids())
					.and(StringUtils.hasText(keyword), query -> query.like(OcpxAdvertiser::getAdvertiserName, keyword)
						.or()
						.like(OcpxAdvertiser::getAdvertiserCode, keyword))
					.eq(normalizedAdapter != null, OcpxAdvertiser::getAdapterCode, normalizedAdapter)
					.eq(StringUtils.hasText(status), OcpxAdvertiser::getStatus, status)
					.isNotNull(Boolean.TRUE.equals(documentConfigured), OcpxAdvertiser::getCurrentDocumentId)
					.isNull(Boolean.FALSE.equals(documentConfigured), OcpxAdvertiser::getCurrentDocumentId)
					.orderByDesc(OcpxAdvertiser::getUpdateTime))
			.convert(this::listItem);
	}

	public AdvertiserViews.Detail detail(String id) {
		return detailView(requireAdvertiser(id, OcpxAccessScope.PermissionLevel.VIEW));
	}

	@Transactional
	public AdvertiserViews.Detail create(String idempotencyKey, AdvertiserRequests.Create request) {
		accessScope.requireAll(OcpxAccessScope.PermissionLevel.MANAGE);
		String requestKey = validateIdempotencyKey(idempotencyKey);
		String requestHash = createRequestHash(request);
		OcpxAdvertiser existing = advertiserMapper.selectOne(Wrappers.<OcpxAdvertiser>lambdaQuery()
			.eq(OcpxAdvertiser::getCreateRequestKey, requestKey));
		if (existing != null) {
			if (!requestHash.equals(existing.getCreateRequestHash())) {
				throw conflict("幂等键已用于不同的创建请求");
			}
			return detailView(existing);
		}

		AdvertiserRequests.Integration integration = request.integration();
		String adapterCode = integration == null ? null : normalizeAdapter(integration.adapterKey());
		AdvertiserViews.AdapterCapability capability = integration == null ? null
				: adapterService.requireAdvertiserCapability(adapterCode);
		String bidUrl = integration == null ? null
				: validateBidUrl(integration.bidUrl(), integration.upstreamHostAllowlist(), capability.requiresBidUrl());
		ensureUnique(null, request.name(), request.code(), request.account());

		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setAdvertiserCode(request.code().trim());
		advertiser.setAdvertiserName(request.name().trim());
		advertiser.setAdapterCode(adapterCode);
		advertiser.setAdapterVersion(capability == null ? 0L : capability.version());
		advertiser.setBidUrl(bidUrl);
		advertiser.setMacroParams(integration == null ? null : trimToNull(integration.macroParams()));
		advertiser.setUpstreamHostAllowlist(
				integration == null ? null : trimToNull(normalizeAllowlist(integration.upstreamHostAllowlist())));
		advertiser.setAccount(request.account().trim());
		advertiser.setPasswordHash(null);
		advertiser.setBalance(BigDecimal.ZERO);
		advertiser.setStatus("DISABLED");
		advertiser.setConfigVersion(1L);
		advertiser.setCredentialRevision(0L);
		advertiser.setDocumentRevision(0L);
		advertiser.setCreateRequestKey(requestKey);
		advertiser.setCreateRequestHash(requestHash);
		try {
			advertiserMapper.insert(advertiser);
		}
		catch (DuplicateKeyException duplicate) {
			OcpxAdvertiser concurrent = advertiserMapper.selectOne(Wrappers.<OcpxAdvertiser>lambdaQuery()
				.eq(OcpxAdvertiser::getCreateRequestKey, requestKey));
			if (concurrent != null && requestHash.equals(concurrent.getCreateRequestHash())) {
				return detailView(concurrent);
			}
			throw conflict("广告主名称、编码、账号或幂等键已被占用");
		}

		if (integration != null && StringUtils.hasText(integration.credentialInput())) {
			createCredential(advertiser, 1L, integration.credentialInput(), "初始凭据", "ACTIVE", null);
			advertiser.setCredentialRevision(1L);
			advertiserMapper.updateById(advertiser);
		}
		authorizationService.provision(advertiser, request.password(), request.roleIds(), request.extraMenuIds());
		audit(advertiser, "CREATE", 0L, "已创建广告主，默认禁用", 0);
		return detailView(advertiser);
	}

	@Transactional
	public AdvertiserViews.Detail update(String id, AdvertiserRequests.Update request) {
		OcpxAdvertiser advertiser = lockAdvertiser(id, OcpxAccessScope.PermissionLevel.OPERATE);
		long before = version(advertiser);
		if (before != request.expectedVersion()) {
			throw conflict("广告主配置已被其他操作修改，请刷新后重试");
		}
		boolean integrationConfigured = hasIntegrationInput(request.adapterKey(), request.bidUrl(), request.macroParams(),
				request.upstreamHostAllowlist());
		if ("ACTIVE".equals(advertiser.getStatus()) && !integrationConfigured) {
			throw new IllegalArgumentException("启用中的广告主不能清空接入配置，请先禁用");
		}
		if (integrationConfigured && !StringUtils.hasText(request.adapterKey())) {
			throw new IllegalArgumentException("填写接入配置时必须选择上游 Adapter");
		}
		String adapterCode = integrationConfigured ? normalizeAdapter(request.adapterKey()) : null;
		AdvertiserViews.AdapterCapability capability = integrationConfigured
				? adapterService.requireAdvertiserCapability(adapterCode) : null;
		String bidUrl = integrationConfigured
				? validateBidUrl(request.bidUrl(), request.upstreamHostAllowlist(), capability.requiresBidUrl()) : null;
		ensureUnique(advertiser.getId(), request.name(), advertiser.getAdvertiserCode(), advertiser.getAccount());
		advertiser.setAdvertiserName(request.name().trim());
		advertiser.setAdapterCode(adapterCode);
		advertiser.setAdapterVersion(capability == null ? 0L : capability.version());
		advertiser.setBidUrl(bidUrl);
		advertiser.setMacroParams(trimToNull(request.macroParams()));
		advertiser.setUpstreamHostAllowlist(integrationConfigured
				? trimToNull(normalizeAllowlist(request.upstreamHostAllowlist())) : null);
		advertiser.setConfigVersion(before + 1);
		advertiserMapper.updateById(advertiser);
		authorizationService.syncProfile(advertiser);
		int routes = configChangeService.advertiserChanged(id, "广告主接入地址或适配器已变更");
		audit(advertiser, "UPDATE", before, "已更新非敏感接入配置", routes);
		return detailView(advertiser);
	}

	@Transactional
	public AdvertiserViews.CredentialState rotateCredential(String id,
			AdvertiserRequests.RotateCredential request) {
		StpUtil.checkSafe();
		OcpxAdvertiser advertiser = lockAdvertiser(id, OcpxAccessScope.PermissionLevel.MANAGE);
		long currentRevision = number(advertiser.getCredentialRevision());
		if (currentRevision != request.expectedCredentialRevision()) {
			throw conflict("广告主凭据已被其他操作轮换，请刷新后重试");
		}
		LocalDateTime overlapUntil = LocalDateTime.now().plusHours(24);
		if (currentRevision > 0) {
			OcpxAdvertiserCredentialRevision previous = credentialMapper.selectOne(
					Wrappers.<OcpxAdvertiserCredentialRevision>lambdaQuery()
						.eq(OcpxAdvertiserCredentialRevision::getAdvertiserId, id)
						.eq(OcpxAdvertiserCredentialRevision::getCredentialRevision, currentRevision)
						.last("FOR UPDATE"));
			if (previous == null) {
				throw new IllegalStateException("广告主凭据修订元数据缺失");
			}
			if ("LEGACY".equals(previous.getStorageMode())) {
				if (!StringUtils.hasText(advertiser.getSecretKey())) {
					throw new IllegalStateException("存量凭据状态不完整，禁止覆盖");
				}
				previous.setSecretCiphertext(protocolCrypto.encrypt(advertiser.getSecretKey()));
				previous.setStorageMode("ENCRYPTED");
			}
			previous.setStatus("OVERLAP");
			previous.setOverlapUntil(overlapUntil);
			credentialMapper.updateById(previous);
		}

		long nextRevision = currentRevision + 1;
		createCredential(advertiser, nextRevision, request.credentialInput(), request.reason(), "ACTIVE", null);
		long before = version(advertiser);
		advertiser.setCredentialRevision(nextRevision);
		advertiser.setSecretKey(null);
		advertiser.setConfigVersion(before + 1);
		advertiserMapper.updateById(advertiser);
		int routes = configChangeService.advertiserChanged(id, "广告主凭据修订已轮换");
		audit(advertiser, "CREDENTIAL_ROTATE", before, "已轮换凭据修订", routes);
		return new AdvertiserViews.CredentialState(true, nextRevision,
				currentRevision > 0 ? overlapUntil : null, routes);
	}

	@Transactional
	public AdvertiserViews.Mutation changeStatus(String id, AdvertiserRequests.ChangeStatus request) {
		if (!request.confirmed()) {
			throw new IllegalArgumentException("请确认状态变更");
		}
		OcpxAdvertiser advertiser = lockAdvertiser(id, OcpxAccessScope.PermissionLevel.MANAGE);
		long before = version(advertiser);
		if (before != request.expectedVersion()) {
			throw conflict("广告主配置已被其他操作修改，请刷新后重试");
		}
		if (request.status().equals(advertiser.getStatus())) {
			return new AdvertiserViews.Mutation(id, before, republishRequiredCount(id));
		}
		if ("ACTIVE".equals(request.status())) {
			if (!integrationConfigured(advertiser)) {
				throw new IllegalArgumentException("广告主接入配置未完成，禁止启用或发布");
			}
			AdvertiserViews.AdapterCapability capability = adapterService
				.requireAdvertiserCapability(advertiser.getAdapterCode());
			validateBidUrl(advertiser.getBidUrl(), advertiser.getUpstreamHostAllowlist(), capability.requiresBidUrl());
		}
		advertiser.setStatus(request.status());
		advertiser.setConfigVersion(before + 1);
		advertiserMapper.updateById(advertiser);
		int routes = configChangeService.advertiserChanged(id, "广告主状态已变更为 " + request.status());
		audit(advertiser, "STATUS", before,
				"状态已变更为 " + request.status() + "；原因：" + sanitizeReason(request.reason()), routes);
		return new AdvertiserViews.Mutation(id, advertiser.getConfigVersion(), routes);
	}

	public List<AdvertiserViews.Document> documents(String advertiserId) {
		OcpxAdvertiser advertiser = requireAdvertiser(advertiserId, OcpxAccessScope.PermissionLevel.VIEW);
		return documentMapper.selectList(Wrappers.<OcpxIntegrationDocument>lambdaQuery()
			.eq(OcpxIntegrationDocument::getOwnerType, "ADVERTISER")
			.eq(OcpxIntegrationDocument::getOwnerId, advertiserId)
			.orderByDesc(OcpxIntegrationDocument::getCreateTime)).stream()
			.map(document -> documentView(document, advertiser.getCurrentDocumentId()))
			.toList();
	}

	@Transactional
	public AdvertiserViews.Document uploadDocument(String advertiserId, String documentType, String summary,
			MultipartFile file) {
		OcpxAdvertiser advertiser = requireAdvertiser(advertiserId, OcpxAccessScope.PermissionLevel.OPERATE);
		String normalizedType = documentType == null ? "" : documentType.trim().toUpperCase(Locale.ROOT);
		if (!DOCUMENT_TYPES.contains(normalizedType)) {
			throw new IllegalArgumentException("资料类型无效");
		}
		if (file == null || file.isEmpty() || file.getSize() > MAX_DOCUMENT_BYTES) {
			throw new IllegalArgumentException("资料文件必须大于 0 且不超过 10MB");
		}
		String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
		if (!CONTENT_TYPES.contains(contentType)) {
			throw new IllegalArgumentException("资料文件类型不在白名单");
		}
		String fileName = safeFileName(file.getOriginalFilename());
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (Exception error) {
			throw new IllegalArgumentException("无法读取资料文件", error);
		}
		if (new String(bytes, StandardCharsets.US_ASCII).contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE")) {
			throw new IllegalArgumentException("资料文件未通过安全扫描");
		}
		ManagedStorageObject stored = storageUploadService.uploadManaged(new ByteArrayInputStream(bytes), fileName,
				contentType, bytes.length);
		try {
			OcpxIntegrationDocument latest = documentMapper.selectOne(Wrappers.<OcpxIntegrationDocument>lambdaQuery()
				.eq(OcpxIntegrationDocument::getOwnerType, "ADVERTISER")
				.eq(OcpxIntegrationDocument::getOwnerId, advertiserId)
				.eq(OcpxIntegrationDocument::getDocumentType, normalizedType)
				.orderByDesc(OcpxIntegrationDocument::getDocumentVersion)
				.last("LIMIT 1 FOR UPDATE"));
			OcpxIntegrationDocument document = new OcpxIntegrationDocument();
			document.setOwnerType("ADVERTISER");
			document.setOwnerId(advertiserId);
			document.setDocumentType(normalizedType);
			document.setStorageConfigId(stored.storageConfigId());
			document.setStorageProvider(stored.provider());
			document.setObjectKey(stored.objectKey());
			document.setFileName(fileName);
			document.setContentType(contentType);
			document.setFileSize((long) bytes.length);
			document.setSha256(sha256(bytes));
			document.setDocumentVersion(latest == null ? 1L : latest.getDocumentVersion() + 1);
			document.setScanStatus("CLEAN");
			document.setStatus("ACTIVE");
			document.setSummary(trimToNull(summary));
			document.setCreatedBy(actor());
			documentMapper.insert(document);
			audit(advertiser, "DOCUMENT_UPLOAD", version(advertiser), "已上传接入资料版本", 0);
			return documentView(document, advertiser.getCurrentDocumentId());
		}
		catch (RuntimeException error) {
			try {
				storageUploadService.delete(stored);
			}
			catch (RuntimeException ignored) {
				// The managed object remains discoverable by the storage module's orphan cleanup.
			}
			throw error;
		}
	}

	@Transactional
	public AdvertiserViews.Mutation setCurrentDocument(String advertiserId, String documentId,
			AdvertiserRequests.SetCurrentDocument request) {
		OcpxAdvertiser advertiser = lockAdvertiser(advertiserId, OcpxAccessScope.PermissionLevel.OPERATE);
		long before = version(advertiser);
		if (before != request.expectedVersion()) {
			throw conflict("广告主配置已被其他操作修改，请刷新后重试");
		}
		OcpxIntegrationDocument document = requireDocument(advertiserId, documentId);
		if (!"ACTIVE".equals(document.getStatus()) || !"CLEAN".equals(document.getScanStatus())) {
			throw new IllegalArgumentException("只有扫描通过且有效的资料可设为当前版本");
		}
		advertiser.setCurrentDocumentId(documentId);
		advertiser.setDocumentRevision(number(advertiser.getDocumentRevision()) + 1);
		advertiser.setConfigVersion(before + 1);
		advertiserMapper.updateById(advertiser);
		int routes = configChangeService.advertiserChanged(advertiserId, "广告主当前接入资料已变更");
		audit(advertiser, "DOCUMENT_CURRENT", before, "已切换当前接入资料版本", routes);
		return new AdvertiserViews.Mutation(advertiserId, advertiser.getConfigVersion(), routes);
	}

	@Transactional
	public void retireDocument(String advertiserId, String documentId, AdvertiserRequests.RetireDocument request) {
		OcpxAdvertiser advertiser = requireAdvertiser(advertiserId, OcpxAccessScope.PermissionLevel.MANAGE);
		if (documentId.equals(advertiser.getCurrentDocumentId())) {
			throw new IllegalArgumentException("当前生效资料不能停用，请先切换版本");
		}
		if (monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getPublishedDocumentId, documentId)
			.eq(OcpxMonitorLink::getRouteStatus, "PUBLISHED")) > 0) {
			throw new IllegalArgumentException("资料版本仍被已发布路由引用，不能停用");
		}
		OcpxIntegrationDocument document = requireDocument(advertiserId, documentId);
		document.setStatus("RETIRED");
		document.setRetiredBy(actor());
		document.setRetiredAt(LocalDateTime.now());
		documentMapper.updateById(document);
		audit(advertiser, "DOCUMENT_RETIRE", version(advertiser),
				"已停用非当前资料；原因：" + sanitizeReason(request.reason()), 0);
	}

	@Transactional
	public DocumentDownload downloadDocument(String advertiserId, String documentId) {
		OcpxAdvertiser advertiser = requireAdvertiser(advertiserId, OcpxAccessScope.PermissionLevel.VIEW);
		OcpxIntegrationDocument document = requireDocument(advertiserId, documentId);
		if (!"CLEAN".equals(document.getScanStatus())) {
			throw new IllegalArgumentException("资料文件尚未通过安全扫描");
		}
		ManagedStorageObject object = new ManagedStorageObject(document.getStorageConfigId(),
				document.getStorageProvider(), document.getObjectKey(), null);
		audit(advertiser, "DOCUMENT_DOWNLOAD", version(advertiser), "已下载受控接入资料", 0);
		return new DocumentDownload(document.getFileName(), document.getContentType(), document.getFileSize(),
				storageUploadService.open(object));
	}

	private OcpxAdvertiser lockAdvertiser(String id, OcpxAccessScope.PermissionLevel level) {
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, id, level);
		OcpxAdvertiser advertiser = advertiserMapper
			.selectOne(Wrappers.<OcpxAdvertiser>lambdaQuery().eq(OcpxAdvertiser::getId, id).last("FOR UPDATE"));
		if (advertiser == null) {
			throw new IllegalArgumentException("广告主不存在或无权访问");
		}
		return advertiser;
	}

	private OcpxAdvertiser requireAdvertiser(String id, OcpxAccessScope.PermissionLevel level) {
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, id, level);
		OcpxAdvertiser advertiser = advertiserMapper.selectById(id);
		if (advertiser == null) {
			throw new IllegalArgumentException("广告主不存在或无权访问");
		}
		return advertiser;
	}

	private OcpxIntegrationDocument requireDocument(String advertiserId, String documentId) {
		OcpxIntegrationDocument document = documentMapper.selectOne(Wrappers.<OcpxIntegrationDocument>lambdaQuery()
			.eq(OcpxIntegrationDocument::getId, documentId)
			.eq(OcpxIntegrationDocument::getOwnerType, "ADVERTISER")
			.eq(OcpxIntegrationDocument::getOwnerId, advertiserId));
		if (document == null) {
			throw new IllegalArgumentException("接入资料不存在或无权访问");
		}
		return document;
	}

	private void createCredential(OcpxAdvertiser advertiser, long revision, String input, String reason, String status,
			LocalDateTime overlapUntil) {
		OcpxAdvertiserCredentialRevision credential = new OcpxAdvertiserCredentialRevision();
		credential.setAdvertiserId(advertiser.getId());
		credential.setCredentialRef("advcred_" + UUID.randomUUID().toString().replace("-", ""));
		credential.setCredentialRevision(revision);
		credential.setSecretCiphertext(protocolCrypto.encrypt(input));
		credential.setStorageMode("ENCRYPTED");
		credential.setStatus(status);
		credential.setOverlapUntil(overlapUntil);
		credential.setReason(sanitizeReason(reason));
		credential.setCreatedBy(actor());
		credentialMapper.insert(credential);
		advertiser.setCredentialRef(credential.getCredentialRef());
	}

	private void ensureUnique(String id, String name, String code, String account) {
		if (advertiserMapper.selectCount(Wrappers.<OcpxAdvertiser>lambdaQuery()
			.and(query -> query.eq(OcpxAdvertiser::getAdvertiserName, name.trim())
				.or()
				.eq(OcpxAdvertiser::getAdvertiserCode, code.trim())
				.or()
				.eq(OcpxAdvertiser::getAccount, account.trim()))
			.ne(StringUtils.hasText(id), OcpxAdvertiser::getId, id)) > 0) {
			throw conflict("广告主名称、编码或登录账号已存在");
		}
		SysUser linked = StringUtils.hasText(id) ? systemUserMapper.selectOne(
				Wrappers.<SysUser>lambdaQuery().eq(SysUser::getShopId, id).eq(SysUser::getType, "2")) : null;
		if (systemUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
			.eq(SysUser::getUsername, account.trim())
			.ne(linked != null, SysUser::getId, linked == null ? null : linked.getId())) > 0) {
			throw conflict("登录账号已被系统用户占用");
		}
	}

	private String validateBidUrl(String value, String allowlist, boolean required) {
		if (!StringUtils.hasText(value)) {
			if (required) {
				throw new IllegalArgumentException("当前适配器要求配置上游 HTTPS 地址");
			}
			return null;
		}
		try {
			URI uri = URI.create(value.trim());
			if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
					|| uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
				throw new IllegalArgumentException("上游地址必须是无账号和 fragment 的 HTTPS 地址");
			}
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			if (!List.of(normalizeAllowlist(allowlist).split(",")).contains(host)) {
				throw new IllegalArgumentException("上游地址域名必须在白名单中");
			}
			if ("localhost".equals(host) || isPrivateLiteral(host)) {
				throw new IllegalArgumentException("上游地址不能指向本机或私网");
			}
			return uri.toASCIIString();
		}
		catch (IllegalArgumentException error) {
			throw error;
		}
		catch (Exception error) {
			throw new IllegalArgumentException("上游地址无效", error);
		}
	}

	private boolean isPrivateLiteral(String host) {
		if (!host.matches("^[0-9a-fA-F:.]+$")) {
			return false;
		}
		try {
			InetAddress address = InetAddress.getByName(host);
			return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
					|| address.isSiteLocalAddress() || address.isMulticastAddress();
		}
		catch (Exception ignored) {
			return true;
		}
	}

	private AdvertiserViews.ListItem listItem(OcpxAdvertiser advertiser) {
		int routes = republishRequiredCount(advertiser.getId());
		return new AdvertiserViews.ListItem(advertiser.getId(), advertiser.getAdvertiserName(),
				advertiser.getAdvertiserCode(), advertiser.getAdapterCode(), number(advertiser.getAdapterVersion()),
				maskBidUrl(advertiser.getBidUrl()), credentialConfigured(advertiser),
				number(advertiser.getCredentialRevision()), number(advertiser.getDocumentRevision()), advertiser.getBalance(),
				advertiser.getBalance() != null && advertiser.getBalance().signum() > 0 ? "AVAILABLE" : "EMPTY",
				advertiser.getStatus(), version(advertiser), routes > 0, integrationConfigured(advertiser),
				advertiser.getUpdateTime());
	}

	private AdvertiserViews.Detail detailView(OcpxAdvertiser advertiser) {
		return new AdvertiserViews.Detail(advertiser.getId(), advertiser.getAdvertiserName(),
				advertiser.getAdvertiserCode(), advertiser.getAdapterCode(), number(advertiser.getAdapterVersion()),
				advertiser.getBidUrl(), advertiser.getMacroParams(), advertiser.getUpstreamHostAllowlist(),
				maskAccount(advertiser.getAccount()), credentialConfigured(advertiser),
				number(advertiser.getCredentialRevision()), advertiser.getCurrentDocumentId(),
				number(advertiser.getDocumentRevision()), advertiser.getBalance(), advertiser.getStatus(),
				version(advertiser), republishRequiredCount(advertiser.getId()), integrationConfigured(advertiser),
				advertiser.getCreateTime(),
				advertiser.getUpdateTime());
	}

	private AdvertiserViews.Document documentView(OcpxIntegrationDocument document, String currentDocumentId) {
		return new AdvertiserViews.Document(document.getId(), document.getDocumentType(), document.getFileName(),
				document.getContentType(), document.getFileSize(), document.getDocumentVersion(), document.getScanStatus(),
				document.getStatus(), document.getSummary(), document.getCreatedBy(), document.getCreateTime(),
				document.getId().equals(currentDocumentId));
	}

	private int republishRequiredCount(String advertiserId) {
		return Math.toIntExact(monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getAdvertiserId, advertiserId)
			.eq(OcpxMonitorLink::getRouteStatus, "PUBLISHED")
			.eq(OcpxMonitorLink::getRepublishRequired, true)));
	}

	private void audit(OcpxAdvertiser advertiser, String action, long beforeVersion, String summary, int routes) {
		OcpxAdvertiserChangeAudit audit = new OcpxAdvertiserChangeAudit();
		audit.setAdvertiserId(advertiser.getId());
		audit.setAction(action);
		audit.setBeforeConfigVersion(beforeVersion);
		audit.setAfterConfigVersion(version(advertiser));
		audit.setCredentialRevision(number(advertiser.getCredentialRevision()));
		audit.setDocumentRevision(number(advertiser.getDocumentRevision()));
		audit.setAffectedRouteCount(routes);
		audit.setActor(actor());
		audit.setChangeSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);
		auditMapper.insert(audit);
	}

	private boolean credentialConfigured(OcpxAdvertiser advertiser) {
		return number(advertiser.getCredentialRevision()) > 0
				&& (StringUtils.hasText(advertiser.getCredentialRef()) || StringUtils.hasText(advertiser.getSecretKey()));
	}

	private String validateIdempotencyKey(String value) {
		if (!StringUtils.hasText(value) || !value.matches("^[A-Za-z0-9_-]{16,64}$")) {
			throw new IllegalArgumentException("Idempotency-Key 必须为 16 到 64 位字母、数字、下划线或连字符");
		}
		return value;
	}

	private String createRequestHash(AdvertiserRequests.Create request) {
		AdvertiserRequests.Integration integration = request.integration();
		String adapter = integration == null ? "" : normalizeAdapter(integration.adapterKey());
		return sha256(String.join("|", request.name().trim(), request.code().trim(), adapter,
				String.valueOf(integration == null ? null : integration.bidUrl()),
				String.valueOf(integration == null ? null : integration.macroParams()),
				integration == null ? "" : normalizeAllowlist(integration.upstreamHostAllowlist()), request.account().trim(),
				String.join(",", normalized(request.roleIds())), String.join(",", normalized(request.extraMenuIds())),
				String.valueOf(integration != null && StringUtils.hasText(integration.credentialInput())))
			.getBytes(StandardCharsets.UTF_8));
	}

	private boolean integrationConfigured(OcpxAdvertiser advertiser) {
		return StringUtils.hasText(advertiser.getAdapterCode()) && number(advertiser.getAdapterVersion()) > 0;
	}

	private boolean hasIntegrationInput(String adapterKey, String bidUrl, String macroParams, String allowlist) {
		return StringUtils.hasText(adapterKey) || StringUtils.hasText(bidUrl) || StringUtils.hasText(macroParams)
				|| StringUtils.hasText(allowlist);
	}

	private List<String> normalized(List<String> values) {
		return values == null ? List.of()
				: values.stream().filter(StringUtils::hasText).map(String::trim).distinct().sorted().toList();
	}

	private String safeFileName(String value) {
		String fileName = value == null ? "document" : value.replace('\\', '/');
		fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
		if (!StringUtils.hasText(fileName) || fileName.length() > 255 || fileName.contains("..")) {
			throw new IllegalArgumentException("资料文件名无效");
		}
		return fileName;
	}

	private String sanitizeReason(String value) {
		if (!StringUtils.hasText(value)) {
			return "未填写原因";
		}
		String sanitized = value.trim()
			.replaceAll("[\\r\\n\\t]+", " ")
			.replaceAll("(?i)\\b(secret|password|token|api[-_]?key|credential)\\b\\s*[:=]\\s*\\S+",
					"$1=[REDACTED]")
			.replaceAll("[A-Za-z0-9+/=_-]{32,}", "[REDACTED]");
		return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
	}

	private String sha256(byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法计算资料摘要", error);
		}
	}

	private String maskBidUrl(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			URI uri = URI.create(value);
			return uri.getScheme() + "://" + uri.getHost() + "/…";
		}
		catch (Exception ignored) {
			return "https://***/…";
		}
	}

	private String maskAccount(String value) {
		if (!StringUtils.hasText(value) || value.length() < 3) {
			return "***";
		}
		return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
	}

	private String normalizeAdapter(String value) {
		return value.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeAllowlist(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return String.join(",", List.of(value.toLowerCase(Locale.ROOT).split("[,\\s]+")).stream()
			.map(String::trim)
			.filter(StringUtils::hasText)
			.distinct()
			.sorted()
			.toList());
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private String actor() {
		String actor = SecurityUtils.getUserName();
		return StringUtils.hasText(actor) ? actor : "unknown";
	}

	private long version(OcpxAdvertiser advertiser) {
		return advertiser.getConfigVersion() == null ? 0 : advertiser.getConfigVersion();
	}

	private long number(Long value) {
		return value == null ? 0 : value;
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

	public record DocumentDownload(String fileName, String contentType, long size, InputStream inputStream) {
	}

}
