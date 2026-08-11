package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.MediaAccountRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaAccountViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxIntegrationDocument;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccountChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccountOwner;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaCredential;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaCredentialRevision;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxIntegrationDocumentMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountOwnerMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaCredentialMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaCredentialRevisionMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import com.yzytech.scx.system.api.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcpxMediaAccountService {

	private static final String CONFIG_ONLY = "CONFIG_ONLY";

	private final OcpxMediaAccountMapper accountMapper;

	private final OcpxMediaCredentialMapper credentialMapper;

	private final OcpxMediaCredentialRevisionMapper revisionMapper;

	private final OcpxMediaAccountOwnerMapper ownerMapper;

	private final OcpxMediaAccountChangeAuditMapper auditMapper;

	private final OcpxChannelMapper channelMapper;

	private final OcpxIntegrationDocumentMapper documentMapper;

	private final OcpxMonitorLinkMapper monitorMapper;

	private final OcpxSystemUserMapper userMapper;

	private final OcpxAccessScope accessScope;

	private final OcpxUserScopeService userScopeService;

	private final OcpxConfigChangeService configChangeService;

	private final OcpxAdapterService adapterService;

	private final ProtocolCrypto protocolCrypto;

	public IPage<MediaAccountViews.ListItem> list(Page<OcpxMediaAccount> page, String keyword, String channelId,
			String status, Boolean archived) {
		OcpxAccessScope.Snapshot scope = accessScope.current();
		OcpxAccessScope.Restriction channels = scope.restriction(OcpxAccessScope.ScopeType.CHANNEL,
				OcpxAccessScope.PermissionLevel.VIEW);
		OcpxAccessScope.Restriction accounts = scope.restriction(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT,
				OcpxAccessScope.PermissionLevel.VIEW);
		if ((!channels.unrestricted() && channels.ids().isEmpty())
				|| (!accounts.unrestricted() && accounts.ids().isEmpty())) {
			return emptyPage(page);
		}
		if (StringUtils.hasText(channelId)) {
			accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		return accountMapper.selectPage(page, Wrappers.<OcpxMediaAccount>lambdaQuery()
			.in(!channels.unrestricted(), OcpxMediaAccount::getChannelId, channels.ids())
			.in(!accounts.unrestricted(), OcpxMediaAccount::getId, accounts.ids())
			.eq(StringUtils.hasText(channelId), OcpxMediaAccount::getChannelId, channelId)
			.and(StringUtils.hasText(keyword),
					query -> query.like(OcpxMediaAccount::getName, keyword).or().like(OcpxMediaAccount::getCode, keyword))
			.eq(StringUtils.hasText(status), OcpxMediaAccount::getStatus, normalizeStatus(status))
			.eq(Boolean.TRUE.equals(archived), OcpxMediaAccount::getStatus, "ARCHIVED")
			.ne(!Boolean.TRUE.equals(archived), OcpxMediaAccount::getStatus, "ARCHIVED")
			.orderByDesc(OcpxMediaAccount::getUpdateTime)).convert(this::listItem);
	}

	public MediaAccountViews.Detail detail(String id) {
		return detailView(requireAccount(id, OcpxAccessScope.PermissionLevel.VIEW));
	}

	public List<MediaAccountViews.ChannelOption> channelOptions(String keyword) {
		OcpxAccessScope.Restriction restriction = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.CHANNEL, OcpxAccessScope.PermissionLevel.VIEW);
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			return List.of();
		}
		return channelMapper
			.selectList(Wrappers.<OcpxChannel>lambdaQuery()
				.in(!restriction.unrestricted(), OcpxChannel::getId, restriction.ids())
				.and(StringUtils.hasText(keyword),
						query -> query.like(OcpxChannel::getChannelName, keyword)
							.or()
							.like(OcpxChannel::getChannelCode, keyword))
				.orderByAsc(OcpxChannel::getChannelName))
			.stream()
			.map(this::channelOption)
			.toList();
	}

	public List<MediaAccountViews.DocumentOption> documentOptions(String channelId) {
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		return documentMapper
			.selectList(Wrappers.<OcpxIntegrationDocument>lambdaQuery()
				.eq(OcpxIntegrationDocument::getOwnerType, "CHANNEL")
				.eq(OcpxIntegrationDocument::getOwnerId, channelId)
				.eq(OcpxIntegrationDocument::getStatus, "ACTIVE")
				.eq(OcpxIntegrationDocument::getScanStatus, "CLEAN")
				.orderByDesc(OcpxIntegrationDocument::getDocumentVersion))
			.stream()
			.map(row -> new MediaAccountViews.DocumentOption(row.getId(), row.getDocumentType(),
					number(row.getDocumentVersion()), row.getSummary()))
			.toList();
	}

	public List<MediaAccountViews.OwnerOption> ownerOptions(String accountId) {
		requireAccount(accountId, OcpxAccessScope.PermissionLevel.VIEW);
		return userMapper
			.selectList(Wrappers.<SysUser>lambdaQuery()
				.eq(SysUser::getDelFlag, "0")
				.eq(SysUser::getStatus, "0")
				.orderByAsc(SysUser::getNickname, SysUser::getUsername))
			.stream()
			.map(row -> new MediaAccountViews.OwnerOption(row.getId(), displayName(row), row.getStatus()))
			.toList();
	}

	@Transactional
	public MediaAccountViews.Detail create(String idempotencyKey, MediaAccountRequests.Create request) {
		String requestKey = validateIdempotencyKey(idempotencyKey);
		String requestHash = sha256(request.channelId().trim() + "\n" + normalizeName(request.name()) + "\n"
				+ nullToEmpty(request.documentId()) + "\n" + nullToEmpty(request.description()));
		OcpxMediaAccount existing = accountMapper
			.selectOne(Wrappers.<OcpxMediaAccount>lambdaQuery().eq(OcpxMediaAccount::getCreateRequestKey, requestKey));
		if (existing != null) {
			if (!requestHash.equals(existing.getCreateRequestHash())) {
				throw conflict("幂等键已用于不同的创建请求");
			}
			return detailView(requireAccount(existing.getId(), OcpxAccessScope.PermissionLevel.VIEW));
		}
		OcpxChannel channel = requireChannel(request.channelId(), OcpxAccessScope.PermissionLevel.MANAGE);
		OcpxIntegrationDocument document = optionalDocument(channel.getId(), request.documentId());
		OcpxMediaAccount account = new OcpxMediaAccount();
		account.setChannelId(channel.getId());
		account
			.setCode("MA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
		account.setName(request.name().trim());
		account.setNormalizedName(normalizeName(request.name()));
		account.setDocumentId(document == null ? null : document.getId());
		account.setDocumentVersion(document == null ? 0L : number(document.getDocumentVersion()));
		account.setDescription(trimToNull(request.description()));
		account.setStatus("DISABLED");
		account.setConfigVersion(1L);
		account.setCredentialRevision(0L);
		account.setCreateRequestKey(requestKey);
		account.setCreateRequestHash(requestHash);
		account.setCreateBy(actor());
		account.setUpdateBy(actor());
		try {
			accountMapper.insert(account);
		}
		catch (DuplicateKeyException error) {
			OcpxMediaAccount concurrent = accountMapper.selectOne(
					Wrappers.<OcpxMediaAccount>lambdaQuery().eq(OcpxMediaAccount::getCreateRequestKey, requestKey));
			if (concurrent != null && requestHash.equals(concurrent.getCreateRequestHash())) {
				return detailView(concurrent);
			}
			throw conflict("同一渠道下媒体账号名称或幂等键已被占用");
		}
		userScopeService.ensureMediaAccountScope(SecurityUtils.getUserId(), account.getId());
		audit(account, "CREATE", 0, "已创建媒体账号，默认停用", 0);
		return detailView(account);
	}

	@Transactional
	public MediaAccountViews.Detail update(String id, MediaAccountRequests.Update request) {
		OcpxMediaAccount account = lockAccount(id, OcpxAccessScope.PermissionLevel.OPERATE);
		ensureMutable(account);
		long before = requireVersion(account, request.expectedVersion());
		OcpxIntegrationDocument document = optionalDocument(account.getChannelId(), request.documentId());
		account.setName(request.name().trim());
		account.setNormalizedName(normalizeName(request.name()));
		account.setDocumentId(document == null ? null : document.getId());
		account.setDocumentVersion(document == null ? 0L : number(document.getDocumentVersion()));
		account.setDescription(trimToNull(request.description()));
		account.setConfigVersion(before + 1);
		account.setUpdateBy(actor());
		try {
			accountMapper.updateById(account);
		}
		catch (DuplicateKeyException error) {
			throw conflict("同一渠道下媒体账号名称已被占用");
		}
		int routes = configChangeService.mediaAccountChanged(id, "媒体账号配置已变更");
		audit(account, "UPDATE", before, "已更新非敏感业务账号资料", routes);
		return detailView(account);
	}

	@Transactional
	public MediaAccountViews.Mutation changeStatus(String id, MediaAccountRequests.ChangeStatus request) {
		StpUtil.checkSafe();
		OcpxMediaAccount account = lockAccount(id, OcpxAccessScope.PermissionLevel.MANAGE);
		ensureMutable(account);
		long before = requireVersion(account, request.expectedVersion());
		String status = normalizeStatus(request.status());
		if (status.equals(account.getStatus())) {
			return mutation(account, republishCount(id));
		}
		if ("ACTIVE".equals(status)) {
			MediaAccountViews.Preflight preflight = preflightInternal(account);
			if (!"READY".equals(preflight.status())) {
				throw conflict("配置预检未通过，媒体账号不能启用");
			}
		}
		else {
			ensureNoPublishedReference(id, null, "媒体账号仍被已发布路由引用，不能停用或暂停");
		}
		account.setStatus(status);
		account.setConfigVersion(before + 1);
		account.setUpdateBy(actor());
		accountMapper.updateById(account);
		int routes = configChangeService.mediaAccountChanged(id, "媒体账号状态已变更");
		audit(account, "STATUS", before, "状态已变更；原因：" + sanitizeReason(request.reason()), routes);
		return mutation(account, routes);
	}

	@Transactional
	public MediaAccountViews.Mutation archive(String id, MediaAccountRequests.VersionedAction request) {
		StpUtil.checkSafe();
		OcpxMediaAccount account = lockAccount(id, OcpxAccessScope.PermissionLevel.MANAGE);
		ensureMutable(account);
		long before = requireVersion(account, request.expectedVersion());
		ensureNoPublishedReference(id, null, "媒体账号仍被已发布路由引用，不能归档");
		account.setStatus("ARCHIVED");
		account.setArchivedAt(LocalDateTime.now());
		account.setArchivedBy(actor());
		account.setConfigVersion(before + 1);
		account.setUpdateBy(actor());
		accountMapper.updateById(account);
		int routes = configChangeService.mediaAccountChanged(id, "媒体账号已归档");
		audit(account, "ARCHIVE", before, "已归档；原因：" + sanitizeReason(request.reason()), routes);
		return mutation(account, routes);
	}

	@Transactional
	public MediaAccountViews.Mutation restore(String id, MediaAccountRequests.VersionedAction request) {
		StpUtil.checkSafe();
		OcpxMediaAccount account = lockAccount(id, OcpxAccessScope.PermissionLevel.MANAGE);
		long before = requireVersion(account, request.expectedVersion());
		if (!"ARCHIVED".equals(account.getStatus())) {
			throw new IllegalArgumentException("只有已归档媒体账号可以恢复");
		}
		requireChannel(account.getChannelId(), OcpxAccessScope.PermissionLevel.MANAGE);
		account.setStatus("DISABLED");
		account.setArchivedAt(null);
		account.setArchivedBy(null);
		account.setConfigVersion(before + 1);
		account.setUpdateBy(actor());
		accountMapper.updateById(account);
		userScopeService.ensureMediaAccountScope(SecurityUtils.getUserId(), account.getId());
		audit(account, "RESTORE", before, "已恢复为停用状态；原因：" + sanitizeReason(request.reason()), 0);
		return mutation(account, 0);
	}

	public List<MediaAccountViews.Credential> credentials(String accountId) {
		requireAccount(accountId, OcpxAccessScope.PermissionLevel.VIEW);
		return credentialMapper
			.selectList(Wrappers.<OcpxMediaCredential>lambdaQuery()
				.eq(OcpxMediaCredential::getMediaAccountId, accountId)
				.orderByDesc(OcpxMediaCredential::getCreateTime))
			.stream()
			.map(this::credentialView)
			.toList();
	}

	@Transactional
	public MediaAccountViews.Credential createCredential(String accountId,
			MediaAccountRequests.CreateCredential request) {
		StpUtil.checkSafe();
		OcpxMediaAccount account = lockAccount(accountId, OcpxAccessScope.PermissionLevel.MANAGE);
		ensureMutable(account);
		String identifier = normalizeIdentifier(request.loginIdentifier());
		OcpxMediaCredential credential = new OcpxMediaCredential();
		credential.setMediaAccountId(accountId);
		credential.setLoginType("EMAIL");
		credential.setLoginIdentifierCiphertext(protocolCrypto.encrypt(identifier));
		credential.setLoginIdentifierHash(protocolCrypto.blindIndex("media-login:" + accountId, identifier));
		credential.setMaskedIdentifier(maskEmail(identifier));
		credential.setCurrentCredentialRef(credentialRef());
		credential.setCurrentSecretRevision(1L);
		credential.setDescription(trimToNull(request.description()));
		credential.setStatus("PENDING");
		credential.setVersion(1L);
		credential.setCreateBy(actor());
		credential.setUpdateBy(actor());
		try {
			credentialMapper.insert(credential);
		}
		catch (DuplicateKeyException error) {
			throw conflict("该媒体登录账号已存在");
		}
		insertRevision(credential, 1, request.secretInput(), "初始 Secret", "ACTIVE", null);
		long before = version(account);
		account.setCredentialRevision(number(account.getCredentialRevision()) + 1);
		account.setConfigVersion(before + 1);
		account.setUpdateBy(actor());
		accountMapper.updateById(account);
		int routes = configChangeService.mediaAccountChanged(accountId, "媒体凭据已新增");
		audit(account, "CREDENTIAL_CREATE", before, "已新增只写凭据", routes);
		return credentialView(credential);
	}

	@Transactional
	public MediaAccountViews.Credential updateCredential(String accountId, String credentialId,
			MediaAccountRequests.UpdateCredential request) {
		StpUtil.checkSafe();
		OcpxMediaAccount account = lockAccount(accountId, OcpxAccessScope.PermissionLevel.MANAGE);
		ensureMutable(account);
		OcpxMediaCredential credential = lockCredential(accountId, credentialId);
		if (number(credential.getVersion()) != request.expectedVersion()) {
			throw conflict("媒体凭据已被其他操作修改，请刷新后重试");
		}
		if (StringUtils.hasText(request.loginIdentifier())) {
			String identifier = normalizeIdentifier(request.loginIdentifier());
			credential.setLoginIdentifierCiphertext(protocolCrypto.encrypt(identifier));
			credential.setLoginIdentifierHash(protocolCrypto.blindIndex("media-login:" + accountId, identifier));
			credential.setMaskedIdentifier(maskEmail(identifier));
		}
		credential.setDescription(trimToNull(request.description()));
		credential.setVersion(number(credential.getVersion()) + 1);
		credential.setUpdateBy(actor());
		try {
			credentialMapper.updateById(credential);
		}
		catch (DuplicateKeyException error) {
			throw conflict("该媒体登录账号已存在");
		}
		long before = bumpAccountCredential(account);
		int routes = configChangeService.mediaAccountChanged(accountId, "媒体凭据元数据已变更");
		audit(account, "CREDENTIAL_UPDATE", before, "已更新凭据掩码元数据", routes);
		return credentialView(credential);
	}

	@Transactional
	public MediaAccountViews.Credential rotateSecret(String accountId, String credentialId,
			MediaAccountRequests.RotateSecret request) {
		StpUtil.checkSafe();
		OcpxMediaAccount account = lockAccount(accountId, OcpxAccessScope.PermissionLevel.MANAGE);
		ensureMutable(account);
		OcpxMediaCredential credential = lockCredential(accountId, credentialId);
		long current = number(credential.getCurrentSecretRevision());
		if (current != request.expectedSecretRevision()) {
			throw conflict("媒体凭据 Secret 已被其他操作轮换，请刷新后重试");
		}
		LocalDateTime overlapUntil = LocalDateTime.now().plusHours(24);
		OcpxMediaCredentialRevision previous = revisionMapper
			.selectOne(Wrappers.<OcpxMediaCredentialRevision>lambdaQuery()
				.eq(OcpxMediaCredentialRevision::getCredentialId, credentialId)
				.eq(OcpxMediaCredentialRevision::getSecretRevision, current)
				.last("FOR UPDATE"));
		if (previous == null) {
			throw new IllegalStateException("媒体凭据修订元数据缺失");
		}
		previous.setStatus("OVERLAP");
		previous.setOverlapUntil(overlapUntil);
		revisionMapper.updateById(previous);
		long next = current + 1;
		credential.setCurrentCredentialRef(credentialRef());
		credential.setCurrentSecretRevision(next);
		credential.setVersion(number(credential.getVersion()) + 1);
		credential.setUpdateBy(actor());
		insertRevision(credential, next, request.secretInput(), request.reason(), "ACTIVE", null);
		credentialMapper.updateById(credential);
		long before = bumpAccountCredential(account);
		int routes = configChangeService.mediaAccountChanged(accountId, "媒体凭据 Secret 修订已轮换");
		audit(account, "CREDENTIAL_ROTATE", before, "已轮换只写 Secret 修订", routes);
		return credentialView(credential);
	}

	@Transactional
	public MediaAccountViews.Credential changeCredentialStatus(String accountId, String credentialId,
			MediaAccountRequests.ChangeCredentialStatus request) {
		StpUtil.checkSafe();
		OcpxMediaAccount account = lockAccount(accountId, OcpxAccessScope.PermissionLevel.MANAGE);
		ensureMutable(account);
		OcpxMediaCredential credential = lockCredential(accountId, credentialId);
		if (number(credential.getVersion()) != request.expectedVersion()) {
			throw conflict("媒体凭据已被其他操作修改，请刷新后重试");
		}
		if (!"ACTIVE".equals(request.status())) {
			ensureNoPublishedReference(accountId, credentialId, "媒体凭据仍被已发布路由引用，不能停用或撤销");
		}
		credential.setStatus(request.status());
		credential.setVersion(number(credential.getVersion()) + 1);
		credential.setUpdateBy(actor());
		credentialMapper.updateById(credential);
		if ("ACTIVE".equals(request.status()) && !StringUtils.hasText(account.getDefaultCredentialId())) {
			account.setDefaultCredentialId(credentialId);
		}
		if (!"ACTIVE".equals(request.status()) && credentialId.equals(account.getDefaultCredentialId())) {
			account.setDefaultCredentialId(null);
		}
		long before = bumpAccountCredential(account);
		int routes = configChangeService.mediaAccountChanged(accountId, "媒体凭据状态已变更");
		audit(account, "CREDENTIAL_STATUS", before, "凭据状态已变更；原因：" + sanitizeReason(request.reason()), routes);
		return credentialView(credential);
	}

	public List<MediaAccountViews.Owner> owners(String accountId) {
		requireAccount(accountId, OcpxAccessScope.PermissionLevel.VIEW);
		return ownerMapper
			.selectList(Wrappers.<OcpxMediaAccountOwner>lambdaQuery()
				.eq(OcpxMediaAccountOwner::getMediaAccountId, accountId)
				.eq(OcpxMediaAccountOwner::getStatus, "ACTIVE")
				.orderByAsc(OcpxMediaAccountOwner::getCreateTime))
			.stream()
			.map(this::ownerView)
			.toList();
	}

	@Transactional
	public List<MediaAccountViews.Owner> replaceOwners(String accountId, MediaAccountRequests.ReplaceOwners request) {
		OcpxMediaAccount account = lockAccount(accountId, OcpxAccessScope.PermissionLevel.MANAGE);
		ensureMutable(account);
		long before = requireVersion(account, request.expectedVersion());
		Set<String> requested = new HashSet<>(request.userIds());
		if (requested.size() != request.userIds().size()) {
			throw new IllegalArgumentException("负责人不能重复");
		}
		for (String userId : requested) {
			SysUser user = userMapper.selectById(userId);
			if (user == null || !"0".equals(user.getDelFlag()) || !"0".equals(user.getStatus())) {
				throw new IllegalArgumentException("负责人不存在或已停用");
			}
		}
		List<OcpxMediaAccountOwner> rows = ownerMapper.selectList(Wrappers.<OcpxMediaAccountOwner>lambdaQuery()
			.eq(OcpxMediaAccountOwner::getMediaAccountId, accountId)
			.eq(OcpxMediaAccountOwner::getResponsibility, "PRIMARY")
			.last("FOR UPDATE"));
		for (OcpxMediaAccountOwner row : rows) {
			boolean active = requested.remove(row.getUserId());
			row.setStatus(active ? "ACTIVE" : "DISABLED");
			row.setVersion(number(row.getVersion()) + 1);
			row.setUpdateBy(actor());
			ownerMapper.updateById(row);
		}
		for (String userId : requested) {
			OcpxMediaAccountOwner row = new OcpxMediaAccountOwner();
			row.setMediaAccountId(accountId);
			row.setUserId(userId);
			row.setResponsibility("PRIMARY");
			row.setStatus("ACTIVE");
			row.setVersion(1L);
			row.setCreateBy(actor());
			row.setUpdateBy(actor());
			ownerMapper.insert(row);
		}
		account.setConfigVersion(before + 1);
		account.setUpdateBy(actor());
		accountMapper.updateById(account);
		audit(account, "OWNER_REPLACE", before, "已更新内部负责人", 0);
		return owners(accountId);
	}

	@Transactional
	public MediaAccountViews.Preflight preflight(String accountId, MediaAccountRequests.Preflight request) {
		OcpxMediaAccount account = lockAccount(accountId, OcpxAccessScope.PermissionLevel.MANAGE);
		requireVersion(account, request.expectedVersion());
		MediaAccountViews.Preflight result = preflightInternal(account);
		List<OcpxMediaCredential> credentials = credentialMapper.selectList(
				Wrappers.<OcpxMediaCredential>lambdaQuery().eq(OcpxMediaCredential::getMediaAccountId, accountId));
		for (OcpxMediaCredential credential : credentials) {
			credential.setLastPreflightStatus(result.status());
			credential.setLastPreflightAt(result.checkedAt());
			credential.setUpdateBy(actor());
			credentialMapper.updateById(credential);
		}
		audit(account, "CONFIG_PREFLIGHT", version(account), "已执行仅配置预检，未发起网络请求", 0);
		return result;
	}

	private MediaAccountViews.Preflight preflightInternal(OcpxMediaAccount account) {
		List<MediaAccountViews.Check> checks = new ArrayList<>();
		OcpxChannel channel = channelMapper.selectById(account.getChannelId());
		if (channel == null || !"ACTIVE".equals(channel.getStatus())) {
			checks.add(check("CHANNEL_ACTIVE", "BLOCK", "协议渠道未启用"));
		}
		else {
			checks.add(check("CHANNEL_ACTIVE", "PASS", "协议渠道已启用"));
			try {
				adapterService.requireEnabled(channel.getAdapterCode(), "DOWNSTREAM");
				checks.add(check("ADAPTER_AVAILABLE", "PASS", "下游适配器已注册并启用"));
			}
			catch (IllegalArgumentException error) {
				checks.add(check("ADAPTER_AVAILABLE", "BLOCK", "下游适配器未注册或未启用"));
			}
		}
		if (!StringUtils.hasText(account.getDocumentId())) {
			checks.add(check("DOCUMENT", "WARN", "未关联渠道接入资料，不阻断 S3 配置预检"));
		}
		else {
			try {
				optionalDocument(account.getChannelId(), account.getDocumentId());
				checks.add(check("DOCUMENT", "PASS", "渠道接入资料版本有效"));
			}
			catch (IllegalArgumentException error) {
				checks.add(check("DOCUMENT", "BLOCK", "渠道接入资料版本已失效"));
			}
		}
		long activeCredentials = credentialMapper.selectCount(Wrappers.<OcpxMediaCredential>lambdaQuery()
			.eq(OcpxMediaCredential::getMediaAccountId, account.getId())
			.eq(OcpxMediaCredential::getStatus, "ACTIVE"));
		if (activeCredentials == 0 || !StringUtils.hasText(account.getDefaultCredentialId())) {
			checks.add(check("ACTIVE_CREDENTIAL", "BLOCK", "至少需要一个已启用的默认凭据"));
		}
		else {
			OcpxMediaCredential defaultCredential = credentialMapper.selectById(account.getDefaultCredentialId());
			if (defaultCredential == null || !account.getId().equals(defaultCredential.getMediaAccountId())
					|| !"ACTIVE".equals(defaultCredential.getStatus())) {
				checks.add(check("ACTIVE_CREDENTIAL", "BLOCK", "默认凭据无效"));
			}
			else {
				checks.add(check("ACTIVE_CREDENTIAL", "PASS", "默认凭据修订有效"));
			}
		}
		if ("ARCHIVED".equals(account.getStatus())) {
			checks.add(check("ACCOUNT_STATE", "BLOCK", "媒体账号已归档"));
		}
		String result = checks.stream().anyMatch(item -> "BLOCK".equals(item.level())) ? "BLOCKED" : "READY";
		return new MediaAccountViews.Preflight(CONFIG_ONLY, false, result, checks, version(account),
				number(account.getCredentialRevision()), LocalDateTime.now());
	}

	private MediaAccountViews.ListItem listItem(OcpxMediaAccount account) {
		OcpxChannel channel = channelMapper.selectById(account.getChannelId());
		long credentials = credentialMapper.selectCount(
				Wrappers.<OcpxMediaCredential>lambdaQuery().eq(OcpxMediaCredential::getMediaAccountId, account.getId()));
		long activeCredentials = credentialMapper.selectCount(Wrappers.<OcpxMediaCredential>lambdaQuery()
			.eq(OcpxMediaCredential::getMediaAccountId, account.getId())
			.eq(OcpxMediaCredential::getStatus, "ACTIVE"));
		long owners = ownerMapper.selectCount(Wrappers.<OcpxMediaAccountOwner>lambdaQuery()
			.eq(OcpxMediaAccountOwner::getMediaAccountId, account.getId())
			.eq(OcpxMediaAccountOwner::getStatus, "ACTIVE"));
		MediaAccountViews.ReferenceSummary references = references(account.getId());
		return new MediaAccountViews.ListItem(account.getId(), account.getCode(), account.getChannelId(),
				channel == null ? "已失效渠道" : channel.getChannelName(), channel == null ? null : channel.getAdapterCode(),
				account.getName(), account.getDescription(), account.getStatus(), version(account),
				number(account.getCredentialRevision()), StringUtils.hasText(account.getDocumentId()), credentials,
				activeCredentials, owners, references.publishedRouteCount(), republishCount(account.getId()),
				account.getCreateTime(), account.getUpdateTime());
	}

	private MediaAccountViews.Detail detailView(OcpxMediaAccount account) {
		OcpxChannel channel = channelMapper.selectById(account.getChannelId());
		return new MediaAccountViews.Detail(account.getId(), account.getCode(), account.getChannelId(),
				channel == null ? "已失效渠道" : channel.getChannelName(), channel == null ? null : channel.getAdapterCode(),
				account.getName(), account.getDescription(), account.getDocumentId(),
				number(account.getDocumentVersion()), account.getStatus(), version(account),
				number(account.getCredentialRevision()), account.getDefaultCredentialId(), references(account.getId()),
				account.getArchivedAt(), account.getCreateTime(), account.getUpdateTime());
	}

	private MediaAccountViews.Credential credentialView(OcpxMediaCredential credential) {
		return new MediaAccountViews.Credential(credential.getId(), credential.getLoginType(),
				credential.getMaskedIdentifier(), StringUtils.hasText(credential.getCurrentCredentialRef()),
				number(credential.getCurrentSecretRevision()), credential.getDescription(), credential.getStatus(),
				number(credential.getVersion()), credential.getLastPreflightStatus(), credential.getLastPreflightAt(),
				credential.getCreateTime(), credential.getUpdateTime());
	}

	private MediaAccountViews.Owner ownerView(OcpxMediaAccountOwner row) {
		SysUser user = userMapper.selectById(row.getUserId());
		return new MediaAccountViews.Owner(row.getId(), row.getUserId(), user == null ? "已失效用户" : displayName(user),
				row.getResponsibility(), row.getStatus(), number(row.getVersion()));
	}

	private MediaAccountViews.ChannelOption channelOption(OcpxChannel channel) {
		boolean available;
		try {
			adapterService.requireEnabled(channel.getAdapterCode(), "DOWNSTREAM");
			available = true;
		}
		catch (IllegalArgumentException error) {
			available = false;
		}
		return new MediaAccountViews.ChannelOption(channel.getId(), channel.getChannelCode(), channel.getChannelName(),
				channel.getAdapterCode(), channel.getAuthType(), channel.getStatus(), available);
	}

	private MediaAccountViews.ReferenceSummary references(String accountId) {
		long routes = monitorMapper
			.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery().eq(OcpxMonitorLink::getMediaAccountId, accountId));
		long published = monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getMediaAccountId, accountId)
			.eq(OcpxMonitorLink::getRouteStatus, "PUBLISHED"));
		return new MediaAccountViews.ReferenceSummary(0, routes, published);
	}

	private OcpxMediaAccount requireAccount(String id, OcpxAccessScope.PermissionLevel level) {
		accessScope.require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, id, level);
		OcpxMediaAccount account = accountMapper.selectById(id);
		if (account == null) {
			throw new IllegalArgumentException("媒体账号不存在或无权访问");
		}
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, account.getChannelId(), level);
		return account;
	}

	private OcpxMediaAccount lockAccount(String id, OcpxAccessScope.PermissionLevel level) {
		OcpxMediaAccount account = requireAccount(id, level);
		return accountMapper.selectOne(
				Wrappers.<OcpxMediaAccount>lambdaQuery().eq(OcpxMediaAccount::getId, account.getId()).last("FOR UPDATE"));
	}

	private OcpxChannel requireChannel(String id, OcpxAccessScope.PermissionLevel level) {
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, id, level);
		OcpxChannel channel = channelMapper.selectById(id);
		if (channel == null || !"ACTIVE".equals(channel.getStatus())) {
			throw new IllegalArgumentException("协议渠道不存在、已停用或无权访问");
		}
		return channel;
	}

	private OcpxMediaCredential lockCredential(String accountId, String credentialId) {
		OcpxMediaCredential credential = credentialMapper.selectOne(Wrappers.<OcpxMediaCredential>lambdaQuery()
			.eq(OcpxMediaCredential::getId, credentialId)
			.eq(OcpxMediaCredential::getMediaAccountId, accountId)
			.last("FOR UPDATE"));
		if (credential == null) {
			throw new IllegalArgumentException("媒体凭据不存在或无权访问");
		}
		return credential;
	}

	private OcpxIntegrationDocument optionalDocument(String channelId, String documentId) {
		if (!StringUtils.hasText(documentId)) {
			return null;
		}
		OcpxIntegrationDocument document = documentMapper.selectById(documentId);
		if (document == null || !"CHANNEL".equals(document.getOwnerType()) || !channelId.equals(document.getOwnerId())
				|| !"ACTIVE".equals(document.getStatus()) || !"CLEAN".equals(document.getScanStatus())) {
			throw new IllegalArgumentException("渠道接入资料不存在、未通过扫描或不属于该渠道");
		}
		return document;
	}

	private void insertRevision(OcpxMediaCredential credential, long secretRevision, String secret, String reason,
			String status, LocalDateTime overlapUntil) {
		OcpxMediaCredentialRevision revision = new OcpxMediaCredentialRevision();
		revision.setCredentialId(credential.getId());
		revision.setCredentialRef(credential.getCurrentCredentialRef());
		revision.setSecretRevision(secretRevision);
		revision.setSecretCiphertext(protocolCrypto.encrypt(secret));
		revision.setStatus(status);
		revision.setOverlapUntil(overlapUntil);
		revision.setReason(sanitizeReason(reason));
		revision.setCreatedBy(actor());
		revisionMapper.insert(revision);
	}

	private long bumpAccountCredential(OcpxMediaAccount account) {
		long before = version(account);
		account.setCredentialRevision(number(account.getCredentialRevision()) + 1);
		account.setConfigVersion(before + 1);
		account.setUpdateBy(actor());
		accountMapper.updateById(account);
		return before;
	}

	private void ensureNoPublishedReference(String accountId, String credentialId, String message) {
		long count = monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getMediaAccountId, accountId)
			.eq(StringUtils.hasText(credentialId), OcpxMonitorLink::getMediaCredentialId, credentialId)
			.eq(OcpxMonitorLink::getRouteStatus, "PUBLISHED"));
		if (count > 0) {
			throw conflict(message);
		}
	}

	private void ensureMutable(OcpxMediaAccount account) {
		if ("ARCHIVED".equals(account.getStatus())) {
			throw conflict("已归档媒体账号不能修改，请先恢复");
		}
	}

	private void audit(OcpxMediaAccount account, String action, long before, String summary, int routes) {
		OcpxMediaAccountChangeAudit audit = new OcpxMediaAccountChangeAudit();
		audit.setMediaAccountId(account.getId());
		audit.setAction(action);
		audit.setBeforeConfigVersion(before);
		audit.setAfterConfigVersion(version(account));
		audit.setCredentialRevision(number(account.getCredentialRevision()));
		audit.setOwnerCount(Math.toIntExact(ownerMapper.selectCount(Wrappers.<OcpxMediaAccountOwner>lambdaQuery()
			.eq(OcpxMediaAccountOwner::getMediaAccountId, account.getId())
			.eq(OcpxMediaAccountOwner::getStatus, "ACTIVE"))));
		audit.setAffectedRouteCount(routes);
		audit.setActor(actor());
		audit.setChangeSummary(summary);
		auditMapper.insert(audit);
	}

	private int republishCount(String accountId) {
		return Math.toIntExact(monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getMediaAccountId, accountId)
			.eq(OcpxMonitorLink::getRouteStatus, "PUBLISHED")
			.eq(OcpxMonitorLink::getRepublishRequired, true)));
	}

	private MediaAccountViews.Mutation mutation(OcpxMediaAccount account, int routes) {
		return new MediaAccountViews.Mutation(account.getId(), account.getStatus(), version(account),
				number(account.getCredentialRevision()), routes);
	}

	private long requireVersion(OcpxMediaAccount account, Long expected) {
		long current = version(account);
		if (current != expected) {
			throw conflict("媒体账号已被其他操作修改，请刷新后重试");
		}
		return current;
	}

	private <T> IPage<T> emptyPage(Page<?> page) {
		Page<T> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
		empty.setRecords(List.of());
		return empty;
	}

	private MediaAccountViews.Check check(String code, String level, String message) {
		return new MediaAccountViews.Check(code, level, message);
	}

	private String displayName(SysUser user) {
		return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
	}

	private String normalizeName(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeIdentifier(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeStatus(String value) {
		return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
	}

	private String maskEmail(String value) {
		int at = value.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		String prefix = value.substring(0, at);
		String visible = prefix.substring(0, Math.min(2, prefix.length()));
		return visible + "***" + value.substring(at);
	}

	private String validateIdempotencyKey(String value) {
		if (!StringUtils.hasText(value) || value.length() > 64 || !value.matches("[A-Za-z0-9._:-]+")) {
			throw new IllegalArgumentException("Idempotency-Key 必须为 1-64 位安全字符");
		}
		return value;
	}

	private String credentialRef() {
		return "MC_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
	}

	private String sanitizeReason(String value) {
		if (!StringUtils.hasText(value)) {
			return "未填写";
		}
		String sanitized = value.trim()
			.replaceAll("[\\r\\n\\t]+", " ")
			.replaceAll("(?i)\\b(secret|password|token|api[-_]?key|credential)\\b\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
			.replaceAll("[A-Za-z0-9+/=_-]{32,}", "[REDACTED]");
		return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	private String actor() {
		String actor = SecurityUtils.getUserName();
		return StringUtils.hasText(actor) ? actor : "SYSTEM";
	}

	private long version(OcpxMediaAccount account) {
		return number(account.getConfigVersion());
	}

	private long number(Long value) {
		return value == null ? 0 : value;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法计算请求指纹", error);
		}
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

}
