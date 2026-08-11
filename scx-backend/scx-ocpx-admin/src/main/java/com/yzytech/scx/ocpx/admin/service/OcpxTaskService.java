package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.TaskRequests;
import com.yzytech.scx.ocpx.admin.dto.TaskViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaCredential;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.entity.OcpxTask;
import com.yzytech.scx.ocpx.admin.entity.OcpxTaskChangeAudit;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaCredentialMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxTaskChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxTaskMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcpxTaskService {

	private final OcpxTaskMapper taskMapper;

	private final OcpxTaskChangeAuditMapper auditMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxProductMapper productMapper;

	private final OcpxAppLinkMapper appLinkMapper;

	private final OcpxChannelMapper channelMapper;

	private final OcpxMediaAccountMapper mediaAccountMapper;

	private final OcpxMediaCredentialMapper mediaCredentialMapper;

	private final OcpxSystemUserMapper systemUserMapper;

	private final OcpxMonitorLinkMapper monitorMapper;

	private final OcpxAccessScope accessScope;

	private final OcpxConfigChangeService configChangeService;

	private final OcpxTaskEventBindingService taskEventBindingService;

	public IPage<TaskViews.ListItem> list(Page<OcpxTask> page, String keyword, String advertiserId, String productId,
			String channelId, String mediaAccountId, String operatorUserId, String status, Boolean exposureEnabled,
			Boolean archived) {
		OcpxAccessScope.Snapshot scope = accessScope.current();
		OcpxAccessScope.Restriction advertisers = scope.restriction(OcpxAccessScope.ScopeType.ADVERTISER,
				OcpxAccessScope.PermissionLevel.VIEW);
		OcpxAccessScope.Restriction channels = scope.restriction(OcpxAccessScope.ScopeType.CHANNEL,
				OcpxAccessScope.PermissionLevel.VIEW);
		OcpxAccessScope.Restriction mediaAccounts = scope.restriction(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT,
				OcpxAccessScope.PermissionLevel.VIEW);
		if (StringUtils.hasText(advertiserId)) {
			accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, advertiserId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(channelId)) {
			accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(mediaAccountId)) {
			accessScope.require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, mediaAccountId,
					OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (empty(advertisers) || empty(channels) || empty(mediaAccounts)) {
			Page<TaskViews.ListItem> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
			empty.setRecords(List.of());
			return empty;
		}
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		return taskMapper
			.selectPage(page,
					Wrappers.<OcpxTask>lambdaQuery()
						.in(!advertisers.unrestricted(), OcpxTask::getAdvertiserId, advertisers.ids())
						.in(!channels.unrestricted(), OcpxTask::getChannelId, channels.ids())
						.in(!mediaAccounts.unrestricted(), OcpxTask::getMediaAccountId, mediaAccounts.ids())
						.eq(StringUtils.hasText(advertiserId), OcpxTask::getAdvertiserId, advertiserId)
						.eq(StringUtils.hasText(productId), OcpxTask::getProductId, productId)
						.eq(StringUtils.hasText(channelId), OcpxTask::getChannelId, channelId)
						.eq(StringUtils.hasText(mediaAccountId), OcpxTask::getMediaAccountId, mediaAccountId)
						.eq(StringUtils.hasText(operatorUserId), OcpxTask::getOperatorUserId, operatorUserId)
						.eq(exposureEnabled != null, OcpxTask::getExposureEnabled, exposureEnabled)
						.eq(Boolean.TRUE.equals(archived), OcpxTask::getStatus, "ARCHIVED")
						.ne(!Boolean.TRUE.equals(archived), OcpxTask::getStatus, "ARCHIVED")
						.eq(StringUtils.hasText(status) && !Boolean.TRUE.equals(archived), OcpxTask::getStatus, status)
						.and(StringUtils.hasText(normalizedKeyword),
								query -> query.like(OcpxTask::getTaskName, normalizedKeyword)
									.or()
									.like(OcpxTask::getBizCode, normalizedKeyword)
									.or()
									.like(OcpxTask::getDescription, normalizedKeyword))
						.orderByDesc(OcpxTask::getUpdateTime))
			.convert(this::listItem);
	}

	public TaskViews.Detail detail(String id) {
		return detailView(requireTask(id, OcpxAccessScope.PermissionLevel.VIEW));
	}

	public TaskViews.Options options(String keyword) {
		OcpxAccessScope.Snapshot scope = accessScope.current();
		OcpxAccessScope.Restriction advertisers = scope.restriction(OcpxAccessScope.ScopeType.ADVERTISER,
				OcpxAccessScope.PermissionLevel.VIEW);
		OcpxAccessScope.Restriction channels = scope.restriction(OcpxAccessScope.ScopeType.CHANNEL,
				OcpxAccessScope.PermissionLevel.VIEW);
		OcpxAccessScope.Restriction media = scope.restriction(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT,
				OcpxAccessScope.PermissionLevel.VIEW);
		if (empty(advertisers) || empty(channels) || empty(media)) {
			return new TaskViews.Options(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		}
		String term = StringUtils.hasText(keyword) ? keyword.trim() : null;
		List<OcpxAdvertiser> advertiserRows = advertiserMapper.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery()
			.in(!advertisers.unrestricted(), OcpxAdvertiser::getId, advertisers.ids())
			.eq(OcpxAdvertiser::getStatus, "ACTIVE")
			.and(StringUtils.hasText(term),
					q -> q.like(OcpxAdvertiser::getAdvertiserName, term)
						.or()
						.like(OcpxAdvertiser::getAdvertiserCode, term))
			.orderByAsc(OcpxAdvertiser::getAdvertiserName)
			.last("LIMIT 100"));
		List<OcpxProduct> productRows = productMapper.selectList(Wrappers.<OcpxProduct>lambdaQuery()
			.in(!advertisers.unrestricted(), OcpxProduct::getAdvertiserId, advertisers.ids())
			.eq(OcpxProduct::getStatus, "ACTIVE")
			.orderByAsc(OcpxProduct::getName)
			.last("LIMIT 200"));
		List<OcpxAppLink> appLinks = appLinkMapper.selectList(Wrappers.<OcpxAppLink>lambdaQuery()
			.in(!advertisers.unrestricted(), OcpxAppLink::getAdvertiserId, advertisers.ids())
			.eq(OcpxAppLink::getStatus, "ACTIVE")
			.eq(OcpxAppLink::getMappingStatus, "READY")
			.orderByAsc(OcpxAppLink::getLinkName)
			.last("LIMIT 300"));
		List<OcpxChannel> channelRows = channelMapper.selectList(Wrappers.<OcpxChannel>lambdaQuery()
			.in(!channels.unrestricted(), OcpxChannel::getId, channels.ids())
			.eq(OcpxChannel::getStatus, "ACTIVE")
			.orderByAsc(OcpxChannel::getChannelName)
			.last("LIMIT 100"));
		List<OcpxMediaAccount> accountRows = mediaAccountMapper.selectList(Wrappers.<OcpxMediaAccount>lambdaQuery()
			.in(!media.unrestricted(), OcpxMediaAccount::getId, media.ids())
			.in(!channels.unrestricted(), OcpxMediaAccount::getChannelId, channels.ids())
			.eq(OcpxMediaAccount::getStatus, "ACTIVE")
			.orderByAsc(OcpxMediaAccount::getName)
			.last("LIMIT 200"));
		List<String> accountIds = accountRows.stream().map(OcpxMediaAccount::getId).toList();
		List<OcpxMediaCredential> credentials = accountIds.isEmpty() ? List.of()
				: mediaCredentialMapper.selectList(Wrappers.<OcpxMediaCredential>lambdaQuery()
					.in(OcpxMediaCredential::getMediaAccountId, accountIds)
					.eq(OcpxMediaCredential::getStatus, "ACTIVE")
					.orderByAsc(OcpxMediaCredential::getMediaAccountId, OcpxMediaCredential::getId)
					.last("LIMIT 300"));
		List<SysUser> operators = systemUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
			.eq(SysUser::getStatus, "0")
			.eq(SysUser::getDelFlag, "0")
			.and(StringUtils.hasText(term),
					q -> q.like(SysUser::getUsername, term).or().like(SysUser::getNickname, term))
			.orderByAsc(SysUser::getUsername)
			.last("LIMIT 100"));
		return new TaskViews.Options(
				advertiserRows.stream()
					.map(row -> new TaskViews.AdvertiserOption(row.getId(), row.getAdvertiserName()))
					.toList(),
				productRows.stream()
					.map(row -> new TaskViews.ProductOption(row.getId(), row.getAdvertiserId(), row.getName()))
					.toList(),
				appLinks.stream()
					.map(row -> new TaskViews.AppLinkOption(row.getId(), row.getAdvertiserId(), row.getProductId(),
							row.getLinkName(), row.getLinkType()))
					.toList(),
				channelRows.stream()
					.map(row -> new TaskViews.ChannelOption(row.getId(), row.getChannelName()))
					.toList(),
				accountRows.stream()
					.map(row -> new TaskViews.MediaAccountOption(row.getId(), row.getChannelId(), row.getName(),
							row.getDefaultCredentialId()))
					.toList(),
				credentials.stream()
					.map(row -> new TaskViews.CredentialOption(row.getId(), row.getMediaAccountId(),
							"凭据 · " + suffix(row.getId()), number(row.getCurrentSecretRevision()), row.getStatus()))
					.toList(),
				operators.stream()
					.map(row -> new TaskViews.OperatorOption(row.getId(),
							StringUtils.hasText(row.getNickname()) ? row.getNickname() : row.getUsername()))
					.toList());
	}

	@Transactional
	public TaskViews.Detail create(String idempotencyKey, TaskRequests.Create request) {
		String requestKey = idempotencyKey(idempotencyKey);
		Structure structure = validate(request.productId(), request.clickAppLinkId(), request.exposureAppLinkId(),
				request.mediaAccountId(), request.mediaCredentialId(), request.operatorUserId(), request.taskName(),
				request.exposureEnabled(), request.exposureAsClick(), request.dedupWindowSeconds(), request.rtaMode(),
				request.description(), OcpxAccessScope.PermissionLevel.OPERATE);
		String hash = requestHash(structure, request.taskName(), request.exposureEnabled(), request.exposureAsClick(),
				request.dedupWindowSeconds(), request.description(), request.eventBindings());
		OcpxTask existing = taskMapper
			.selectOne(Wrappers.<OcpxTask>lambdaQuery().eq(OcpxTask::getCreateRequestKey, requestKey));
		if (existing != null) {
			requireTaskScopes(existing, OcpxAccessScope.PermissionLevel.OPERATE);
			if (!hash.equals(existing.getCreateRequestHash())) {
				throw conflict("OCPX_TASK_IDEMPOTENCY_CONFLICT：幂等键已用于不同任务创建请求");
			}
			return detailView(existing);
		}
		ensureUnique(null, structure.advertiser().getId(), request.taskName());
		OcpxTask task = new OcpxTask();
		task.setBizCode(id());
		apply(task, structure, request.taskName(), request.exposureEnabled(), request.exposureAsClick(),
				request.dedupWindowSeconds(), request.description());
		task.setStatus("DRAFT");
		task.setConfigVersion(1L);
		task.setRepublishRequired(true);
		task.setRepublishReason("TASK_DRAFT_SAVED");
		task.setPublishedTaskVersion(0L);
		task.setCreateRequestKey(requestKey);
		task.setCreateRequestHash(hash);
		task.setCreateBy(actor());
		task.setUpdateBy(actor());
		try {
			taskMapper.insert(task);
		}
		catch (DuplicateKeyException duplicate) {
			OcpxTask concurrent = taskMapper
				.selectOne(Wrappers.<OcpxTask>lambdaQuery().eq(OcpxTask::getCreateRequestKey, requestKey));
			if (concurrent != null && hash.equals(concurrent.getCreateRequestHash())) {
				return detailView(concurrent);
			}
			throw conflict("OCPX_TASK_DUPLICATE：同一广告主下任务名称已存在");
		}
		taskEventBindingService.syncWithinTaskSave(task, request.eventBindings(), true, false);
		audit(task, "CREATE", 0, 0, "任务草稿已保存；未发布、未创建 Outbox、未修改旧快照");
		return detailView(task);
	}

	@Transactional
	public TaskViews.Detail update(String id, TaskRequests.Update request) {
		OcpxTask task = lockTask(id, OcpxAccessScope.PermissionLevel.OPERATE);
		if ("ARCHIVED".equals(task.getStatus())) {
			throw conflict("OCPX_TASK_ARCHIVED：归档任务需先恢复");
		}
		requireVersion(task, request.expectedVersion());
		Structure structure = validate(request.productId(), request.clickAppLinkId(), request.exposureAppLinkId(),
				request.mediaAccountId(), request.mediaCredentialId(), request.operatorUserId(), request.taskName(),
				request.exposureEnabled(), request.exposureAsClick(), request.dedupWindowSeconds(), request.rtaMode(),
				request.description(), OcpxAccessScope.PermissionLevel.OPERATE);
		ensureUnique(task.getId(), structure.advertiser().getId(), request.taskName());
		long before = version(task);
		boolean mediaParentChanged = !task.getChannelId().equals(structure.channel().getId())
				|| !task.getMediaAccountId().equals(structure.account().getId());
		apply(task, structure, request.taskName(), request.exposureEnabled(), request.exposureAsClick(),
				request.dedupWindowSeconds(), request.description());
		// Any edit returns readiness to DRAFT. It is never a traffic pause action.
		task.setStatus("DRAFT");
		task.setConfigVersion(before + 1);
		task.setRepublishRequired(true);
		task.setRepublishReason("TASK_DRAFT_UPDATED");
		task.setUpdateBy(actor());
		try {
			taskMapper.updateById(task);
		}
		catch (DuplicateKeyException duplicate) {
			throw conflict("OCPX_TASK_DUPLICATE：同一广告主下任务名称已存在");
		}
		taskEventBindingService.syncWithinTaskSave(task, request.eventBindings(), false, mediaParentChanged);
		int affected = configChangeService.taskChanged(task.getId(), "任务草稿已更新，需显式重发");
		audit(task, "UPDATE", before, affected, "任务草稿已更新并标记依赖待重发；冻结快照未变化");
		return detailView(task);
	}

	@Transactional
	public TaskViews.Mutation changeStatus(String id, TaskRequests.StatusAction request) {
		OcpxTask task = lockTask(id, OcpxAccessScope.PermissionLevel.OPERATE);
		requireVersion(task, request.expectedVersion());
		if (!List.of("DRAFT", "READY").contains(request.status())) {
			throw conflict("OCPX_TASK_STATUS_REQUIRES_S7：ACTIVE/PAUSED 属于发布与停流状态，S5 不伪装为已生效");
		}
		if ("ARCHIVED".equals(task.getStatus())) {
			throw conflict("OCPX_TASK_ARCHIVED：归档任务需先恢复");
		}
		if ("READY".equals(request.status())) {
			validateStored(task, OcpxAccessScope.PermissionLevel.OPERATE);
		}
		long before = version(task);
		task.setStatus(request.status());
		task.setConfigVersion(before + 1);
		task.setRepublishRequired(true);
		task.setRepublishReason("TASK_READINESS_CHANGED");
		task.setUpdateBy(actor());
		taskMapper.updateById(task);
		int affected = configChangeService.taskChanged(task.getId(), "任务就绪状态已变更，需显式重发");
		audit(task, "STATUS_" + request.status(), before, affected,
				"仅变更任务就绪状态；未发布、未停流、未修改冻结快照；原因=" + reason(request.reason()));
		return mutation(task, affected);
	}

	@Transactional
	public TaskViews.Mutation archive(String id, TaskRequests.VersionedAction request) {
		OcpxTask task = lockTask(id, OcpxAccessScope.PermissionLevel.MANAGE);
		requireVersion(task, request.expectedVersion());
		if ("ARCHIVED".equals(task.getStatus())) {
			return mutation(task, 0);
		}
		long references = monitorMapper
			.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery().eq(OcpxMonitorLink::getTaskId, id));
		if (references > 0) {
			throw conflict("OCPX_TASK_REFERENCE_PROTECTED：任务仍被 Monitor 引用，不能归档");
		}
		long before = version(task);
		task.setStatus("ARCHIVED");
		task.setArchivedAt(LocalDateTime.now());
		task.setArchivedBy(actor());
		task.setConfigVersion(before + 1);
		task.setUpdateBy(actor());
		taskMapper.updateById(task);
		audit(task, "ARCHIVE", before, 0, "任务已归档；原因=" + reason(request.reason()));
		return mutation(task, 0);
	}

	@Transactional
	public TaskViews.Mutation restore(String id, TaskRequests.VersionedAction request) {
		OcpxTask task = lockTask(id, OcpxAccessScope.PermissionLevel.MANAGE);
		requireVersion(task, request.expectedVersion());
		if (!"ARCHIVED".equals(task.getStatus())) {
			return mutation(task, 0);
		}
		validateStored(task, OcpxAccessScope.PermissionLevel.MANAGE);
		ensureUnique(task.getId(), task.getAdvertiserId(), task.getTaskName());
		long before = version(task);
		task.setStatus("DRAFT");
		task.setArchivedAt(null);
		task.setArchivedBy(null);
		task.setConfigVersion(before + 1);
		task.setRepublishRequired(true);
		task.setRepublishReason("TASK_RESTORED_AS_DRAFT");
		task.setUpdateBy(actor());
		taskMapper.updateById(task);
		audit(task, "RESTORE", before, 0, "任务已恢复为草稿；原因=" + reason(request.reason()));
		return mutation(task, 0);
	}

	private Structure validateStored(OcpxTask task, OcpxAccessScope.PermissionLevel level) {
		return validate(task.getProductId(), task.getClickAppLinkId(), task.getExposureAppLinkId(),
				task.getMediaAccountId(), task.getMediaCredentialId(), task.getOperatorUserId(), task.getTaskName(),
				task.getExposureEnabled(), task.getExposureAsClick(), task.getDedupWindowSeconds(), task.getRtaMode(),
				task.getDescription(), level);
	}

	private Structure validate(String productId, String clickLinkId, String exposureLinkId, String mediaAccountId,
			String credentialId, String operatorUserId, String taskName, Boolean exposureEnabled,
			Boolean exposureAsClick, Integer dedupWindowSeconds, String rtaMode, String description,
			OcpxAccessScope.PermissionLevel level) {
		if (!"OFF".equals(rtaMode)) {
			throw new IllegalArgumentException("OCPX_TASK_RTA_OFF_ONLY：首期 RTA 固定为 OFF");
		}
		if (dedupWindowSeconds == null || dedupWindowSeconds < 0 || dedupWindowSeconds > 86400) {
			throw new IllegalArgumentException("OCPX_TASK_DEDUP_RANGE：去重窗口必须在 0 到 86400 秒之间");
		}
		if (!StringUtils.hasText(taskName) || taskName.trim().length() > 64) {
			throw new IllegalArgumentException("任务名称必须为 1 到 64 个字符");
		}
		if (description != null && description.trim().length() > 500) {
			throw new IllegalArgumentException("任务说明不能超过 500 个字符");
		}
		if (Boolean.TRUE.equals(exposureEnabled)) {
			if (!StringUtils.hasText(exposureLinkId)) {
				throw new IllegalArgumentException("OCPX_TASK_EXPOSURE_LINK_REQUIRED：开启曝光时必须选择曝光 AppLink");
			}
		}
		else if (StringUtils.hasText(exposureLinkId) || Boolean.TRUE.equals(exposureAsClick)) {
			throw new IllegalArgumentException("OCPX_TASK_EXPOSURE_CLEAR_REQUIRED：关闭曝光时必须清空曝光链接和大点击");
		}
		OcpxProduct product = required(productMapper.selectById(productId), "产品不存在或无权访问");
		OcpxAdvertiser advertiser = required(advertiserMapper.selectById(product.getAdvertiserId()), "广告主不存在或无权访问");
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, advertiser.getId(), level);
		if (!"ACTIVE".equals(product.getStatus()) || !"ACTIVE".equals(advertiser.getStatus())) {
			throw new IllegalArgumentException("广告主和产品必须处于启用状态");
		}
		OcpxAppLink click = requireLink(clickLinkId, product, advertiser);
		OcpxAppLink exposure = StringUtils.hasText(exposureLinkId) ? requireLink(exposureLinkId, product, advertiser)
				: null;
		OcpxMediaAccount account = required(mediaAccountMapper.selectById(mediaAccountId), "媒体账号不存在或无权访问");
		accessScope.require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, account.getId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, account.getChannelId(), level);
		OcpxChannel channel = required(channelMapper.selectById(account.getChannelId()), "渠道不存在或无权访问");
		if (!"ACTIVE".equals(account.getStatus()) || !"ACTIVE".equals(channel.getStatus())) {
			throw new IllegalArgumentException("渠道和媒体账号必须处于启用状态");
		}
		OcpxMediaCredential credential = required(mediaCredentialMapper.selectById(credentialId), "媒体凭据不存在或无权访问");
		if (!account.getId().equals(credential.getMediaAccountId()) || !"ACTIVE".equals(credential.getStatus())
				|| number(credential.getCurrentSecretRevision()) < 1) {
			throw new IllegalArgumentException("媒体凭据未启用、未配置或不属于所选媒体账号");
		}
		SysUser operator = required(systemUserMapper.selectById(operatorUserId), "内部负责人不存在");
		if (!"0".equals(operator.getStatus()) || !"0".equals(operator.getDelFlag())) {
			throw new IllegalArgumentException("内部负责人账号已停用");
		}
		return new Structure(advertiser, product, click, exposure, channel, account, credential, operator);
	}

	private OcpxAppLink requireLink(String id, OcpxProduct product, OcpxAdvertiser advertiser) {
		OcpxAppLink link = required(appLinkMapper.selectById(id), "AppLink 不存在或无权访问");
		if (!advertiser.getId().equals(link.getAdvertiserId()) || !product.getId().equals(link.getProductId())
				|| !"ACTIVE".equals(link.getStatus()) || !"READY".equals(link.getMappingStatus())) {
			throw new IllegalArgumentException("AppLink 未启用、待补映射或与所选 Product/Advertiser 不一致");
		}
		return link;
	}

	private void apply(OcpxTask task, Structure structure, String name, Boolean exposureEnabled, Boolean exposureAsClick,
			Integer dedupWindowSeconds, String description) {
		task.setAdvertiserId(structure.advertiser().getId());
		task.setProductId(structure.product().getId());
		task.setClickAppLinkId(structure.click().getId());
		task.setExposureAppLinkId(Boolean.TRUE.equals(exposureEnabled) ? structure.exposure().getId() : null);
		task.setChannelId(structure.channel().getId());
		task.setMediaAccountId(structure.account().getId());
		task.setMediaCredentialId(structure.credential().getId());
		task.setMediaCredentialSecretRevision(number(structure.credential().getCurrentSecretRevision()));
		task.setOperatorUserId(structure.operator().getId());
		task.setTaskName(name.trim());
		task.setNormalizedName(normalizeName(name));
		task.setExposureEnabled(Boolean.TRUE.equals(exposureEnabled));
		task.setExposureAsClick(Boolean.TRUE.equals(exposureEnabled) && Boolean.TRUE.equals(exposureAsClick));
		task.setDedupWindowSeconds(dedupWindowSeconds);
		task.setRtaMode("OFF");
		task.setDescription(StringUtils.hasText(description) ? description.trim() : null);
	}

	private OcpxTask requireTask(String id, OcpxAccessScope.PermissionLevel level) {
		OcpxTask task = taskMapper.selectById(id);
		if (task == null) {
			throw new IllegalArgumentException("任务不存在或无权访问");
		}
		requireTaskScopes(task, level);
		return task;
	}

	private OcpxTask lockTask(String id, OcpxAccessScope.PermissionLevel level) {
		OcpxTask task = taskMapper.selectOne(Wrappers.<OcpxTask>lambdaQuery().eq(OcpxTask::getId, id).last("FOR UPDATE"));
		if (task == null) {
			throw new IllegalArgumentException("任务不存在或无权访问");
		}
		requireTaskScopes(task, level);
		return task;
	}

	private void requireTaskScopes(OcpxTask task, OcpxAccessScope.PermissionLevel level) {
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, task.getAdvertiserId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, task.getChannelId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, task.getMediaAccountId(), level);
	}

	private void ensureUnique(String id, String advertiserId, String name) {
		long count = taskMapper.selectCount(Wrappers.<OcpxTask>lambdaQuery()
			.eq(OcpxTask::getAdvertiserId, advertiserId)
			.eq(OcpxTask::getNormalizedName, normalizeName(name))
			.ne(OcpxTask::getStatus, "ARCHIVED")
			.ne(StringUtils.hasText(id), OcpxTask::getId, id));
		if (count > 0) {
			throw conflict("OCPX_TASK_DUPLICATE：同一广告主下任务名称已存在");
		}
	}

	private TaskViews.ListItem listItem(OcpxTask task) {
		Names names = names(task);
		return new TaskViews.ListItem(task.getId(), task.getBizCode(), task.getTaskName(), task.getAdvertiserId(),
				names.advertiser(), task.getProductId(), names.product(), task.getClickAppLinkId(), names.clickLink(),
				task.getExposureAppLinkId(), names.exposureLink(), task.getChannelId(), names.channel(),
				task.getMediaAccountId(), names.mediaAccount(), task.getMediaCredentialId(),
				number(task.getMediaCredentialSecretRevision()), task.getOperatorUserId(), names.operator(),
				Boolean.TRUE.equals(task.getExposureEnabled()), Boolean.TRUE.equals(task.getExposureAsClick()),
				task.getDedupWindowSeconds(), task.getRtaMode(), task.getStatus(), version(task),
				Boolean.TRUE.equals(task.getRepublishRequired()), number(task.getPublishedTaskVersion()),
				references(task.getId()), task.getCreateTime(), task.getUpdateTime());
	}

	private TaskViews.Detail detailView(OcpxTask task) {
		Names names = names(task);
		return new TaskViews.Detail(task.getId(), task.getBizCode(), task.getTaskName(), task.getAdvertiserId(),
				names.advertiser(), task.getProductId(), names.product(), task.getClickAppLinkId(), names.clickLink(),
				task.getExposureAppLinkId(), names.exposureLink(), task.getChannelId(), names.channel(),
				task.getMediaAccountId(), names.mediaAccount(), task.getMediaCredentialId(),
				number(task.getMediaCredentialSecretRevision()), task.getOperatorUserId(), names.operator(),
				Boolean.TRUE.equals(task.getExposureEnabled()), Boolean.TRUE.equals(task.getExposureAsClick()),
				task.getDedupWindowSeconds(), task.getRtaMode(), task.getDescription(), task.getStatus(), version(task),
				Boolean.TRUE.equals(task.getRepublishRequired()), task.getRepublishReason(),
				number(task.getPublishedTaskVersion()), references(task.getId()), task.getArchivedAt(),
				task.getCreateTime(), task.getUpdateTime());
	}

	private Names names(OcpxTask task) {
		OcpxAdvertiser advertiser = advertiserMapper.selectById(task.getAdvertiserId());
		OcpxProduct product = productMapper.selectById(task.getProductId());
		OcpxAppLink click = appLinkMapper.selectById(task.getClickAppLinkId());
		OcpxAppLink exposure = StringUtils.hasText(task.getExposureAppLinkId())
				? appLinkMapper.selectById(task.getExposureAppLinkId()) : null;
		OcpxChannel channel = channelMapper.selectById(task.getChannelId());
		OcpxMediaAccount media = mediaAccountMapper.selectById(task.getMediaAccountId());
		SysUser operator = systemUserMapper.selectById(task.getOperatorUserId());
		return new Names(advertiser == null ? "-" : advertiser.getAdvertiserName(),
				product == null ? "-" : product.getName(), click == null ? "-" : click.getLinkName(),
				exposure == null ? null : exposure.getLinkName(), channel == null ? "-" : channel.getChannelName(),
				media == null ? "-" : media.getName(),
				operator == null ? "-" : StringUtils.hasText(operator.getNickname()) ? operator.getNickname()
						: operator.getUsername());
	}

	private TaskViews.ReferenceSummary references(String taskId) {
		long total = monitorMapper
			.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery().eq(OcpxMonitorLink::getTaskId, taskId));
		long published = monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getTaskId, taskId)
			.eq(OcpxMonitorLink::getRouteStatus, "PUBLISHED"));
		long republish = monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getTaskId, taskId)
			.eq(OcpxMonitorLink::getRepublishRequired, true));
		return new TaskViews.ReferenceSummary(total, published, republish);
	}

	private TaskViews.Mutation mutation(OcpxTask task, int affected) {
		return new TaskViews.Mutation(task.getId(), task.getStatus(), version(task),
				Boolean.TRUE.equals(task.getRepublishRequired()), affected);
	}

	private void audit(OcpxTask task, String action, long before, int monitors, String summary) {
		OcpxTaskChangeAudit audit = new OcpxTaskChangeAudit();
		audit.setTaskId(task.getId());
		audit.setAdvertiserId(task.getAdvertiserId());
		audit.setChannelId(task.getChannelId());
		audit.setMediaAccountId(task.getMediaAccountId());
		audit.setAction(action);
		audit.setBeforeConfigVersion(before);
		audit.setAfterConfigVersion(version(task));
		audit.setAffectedMonitorCount(monitors);
		audit.setActor(actor());
		audit.setChangeSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);
		auditMapper.insert(audit);
	}

	private String requestHash(Structure structure, String name, Boolean exposureEnabled, Boolean exposureAsClick,
			Integer dedup, String description, List<com.yzytech.scx.ocpx.admin.dto.EventRequests.TaskBindingInput> bindings) {
		return sha256(String.join("|", structure.product().getId(), structure.click().getId(),
				structure.exposure() == null ? "" : structure.exposure().getId(), structure.account().getId(),
				structure.credential().getId(), String.valueOf(structure.credential().getCurrentSecretRevision()),
				structure.operator().getId(), normalizeName(name), String.valueOf(exposureEnabled),
				String.valueOf(exposureAsClick), String.valueOf(dedup), "OFF",
				StringUtils.hasText(description) ? description.trim() : "", bindingHash(bindings)));
	}

	private String bindingHash(List<com.yzytech.scx.ocpx.admin.dto.EventRequests.TaskBindingInput> bindings) {
		if (bindings == null) {
			return "PRODUCT_DEFAULT_TEMPLATE";
		}
		return bindings.stream()
			.map(binding -> String.join(":", binding.eventDefinitionId(), binding.channelEventMappingId(),
					String.valueOf(binding.callbackRatioBasisPoints()),
					binding.limitValue() == null ? "UNLIMITED" : binding.limitValue().toString(),
					String.valueOf(binding.overLimitDelivery())))
			.sorted()
			.collect(java.util.stream.Collectors.joining(","));
	}

	private void requireVersion(OcpxTask task, long expected) {
		if (version(task) != expected) {
			throw conflict("OCPX_TASK_VERSION_CONFLICT：任务已被其他操作修改，请刷新后重试");
		}
	}

	private boolean empty(OcpxAccessScope.Restriction restriction) {
		return !restriction.unrestricted() && restriction.ids().isEmpty();
	}

	private String idempotencyKey(String value) {
		if (!StringUtils.hasText(value) || !value.matches("^[A-Za-z0-9_-]{16,64}$")) {
			throw new IllegalArgumentException("Idempotency-Key 必须为 16 到 64 位字母、数字、下划线或连字符");
		}
		return value;
	}

	private String normalizeName(String value) {
		return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private String reason(String value) {
		if (!StringUtils.hasText(value)) {
			return "未填写原因";
		}
		String sanitized = value.trim()
			.replaceAll("[\\r\\n\\t]+", " ")
			.replaceAll("(?i)(https?://|secret|password|token|api[-_]?key|credential)[^\\s]*", "[REDACTED]")
			.replaceAll("[A-Za-z0-9+/=_-]{32,}", "[REDACTED]");
		return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
	}

	private String suffix(String id) {
		return id == null || id.length() <= 6 ? "已配置" : id.substring(id.length() - 6);
	}

	private String actor() {
		String actor = SecurityUtils.getUserName();
		return StringUtils.hasText(actor) ? actor : "unknown";
	}

	private long version(OcpxTask task) {
		return number(task.getConfigVersion());
	}

	private long number(Long value) {
		return value == null ? 0 : value;
	}

	private <T> T required(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法计算任务请求摘要", error);
		}
	}

	private record Structure(OcpxAdvertiser advertiser, OcpxProduct product, OcpxAppLink click, OcpxAppLink exposure,
			OcpxChannel channel, OcpxMediaAccount account, OcpxMediaCredential credential, SysUser operator) {
	}

	private record Names(String advertiser, String product, String clickLink, String exposureLink, String channel,
			String mediaAccount, String operator) {
	}

}
