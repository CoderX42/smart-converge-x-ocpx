package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.dto.AppLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.AppLinkViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLinkChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxDeductionRule;
import com.yzytech.scx.ocpx.admin.entity.OcpxIntegrationDocument;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxIntegrationDocumentMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
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
public class OcpxAppLinkService {

	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};

	private final OcpxAppLinkMapper appLinkMapper;

	private final OcpxAppLinkChangeAuditMapper auditMapper;

	private final OcpxProductMapper productMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxIntegrationDocumentMapper documentMapper;

	private final OcpxMonitorLinkMapper monitorMapper;

	private final OcpxDeductionRuleMapper deductionMapper;

	private final OcpxAccessScope accessScope;

	private final OcpxConfigChangeService configChangeService;

	private final OcpxUrlPolicy urlPolicy;

	private final ObjectMapper objectMapper;

	public IPage<AppLinkViews.ListItem> list(Page<OcpxAppLink> page, String keyword, String advertiserId,
			String productId, String linkType, String status, String mappingStatus, Boolean archived) {
		OcpxAccessScope.Restriction restriction = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.ADVERTISER, OcpxAccessScope.PermissionLevel.VIEW);
		if (StringUtils.hasText(advertiserId)) {
			accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, advertiserId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(productId)) {
			requireProduct(productId, OcpxAccessScope.PermissionLevel.VIEW, false);
		}
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			Page<AppLinkViews.ListItem> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
			empty.setRecords(List.of());
			return empty;
		}
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		return appLinkMapper
			.selectPage(page, Wrappers.<OcpxAppLink>lambdaQuery()
				.in(!restriction.unrestricted(), OcpxAppLink::getAdvertiserId, restriction.ids())
				.eq(StringUtils.hasText(advertiserId), OcpxAppLink::getAdvertiserId, advertiserId)
				.eq(StringUtils.hasText(productId), OcpxAppLink::getProductId, productId)
				.eq(StringUtils.hasText(linkType), OcpxAppLink::getLinkType, linkType)
				.eq(StringUtils.hasText(mappingStatus), OcpxAppLink::getMappingStatus, mappingStatus)
				.eq(Boolean.TRUE.equals(archived), OcpxAppLink::getStatus, "ARCHIVED")
				.ne(!Boolean.TRUE.equals(archived), OcpxAppLink::getStatus, "ARCHIVED")
				.eq(StringUtils.hasText(status) && !Boolean.TRUE.equals(archived), OcpxAppLink::getStatus, status)
				.and(StringUtils.hasText(normalizedKeyword),
						query -> query.like(OcpxAppLink::getLinkName, normalizedKeyword)
							.or()
							.like(OcpxAppLink::getDescription, normalizedKeyword)
							.or()
							.like(OcpxAppLink::getBizCode, normalizedKeyword))
				.orderByDesc(OcpxAppLink::getUpdateTime))
			.convert(this::listItem);
	}

	public AppLinkViews.Detail detail(String id) {
		return detailView(requireLink(id, OcpxAccessScope.PermissionLevel.VIEW));
	}

	public List<AppLinkViews.ProductOption> productOptions(String keyword, String advertiserId) {
		OcpxAccessScope.Restriction restriction = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.ADVERTISER, OcpxAccessScope.PermissionLevel.VIEW);
		if (StringUtils.hasText(advertiserId)) {
			accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, advertiserId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			return List.of();
		}
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		return productMapper
			.selectList(Wrappers.<OcpxProduct>lambdaQuery()
				.in(!restriction.unrestricted(), OcpxProduct::getAdvertiserId, restriction.ids())
				.eq(StringUtils.hasText(advertiserId), OcpxProduct::getAdvertiserId, advertiserId)
				.eq(OcpxProduct::getStatus, "ACTIVE")
				.and(StringUtils.hasText(normalizedKeyword),
						query -> query.like(OcpxProduct::getName, normalizedKeyword)
							.or()
							.like(OcpxProduct::getCode, normalizedKeyword))
				.orderByAsc(OcpxProduct::getName)
				.last("LIMIT 100"))
			.stream()
			.map(product -> {
				OcpxAdvertiser advertiser = advertiserMapper.selectById(product.getAdvertiserId());
				return new AppLinkViews.ProductOption(product.getId(), product.getAdvertiserId(),
						advertiser == null ? "-" : advertiser.getAdvertiserName(), product.getCode(),
						product.getName());
			})
			.toList();
	}

	public List<AppLinkViews.DocumentOption> documentOptions(String productId) {
		OcpxProduct product = requireProduct(productId, OcpxAccessScope.PermissionLevel.VIEW, true);
		OcpxAdvertiser advertiser = requireAdvertiser(product.getAdvertiserId(), OcpxAccessScope.PermissionLevel.VIEW,
				true);
		return documentMapper
			.selectList(Wrappers.<OcpxIntegrationDocument>lambdaQuery()
				.eq(OcpxIntegrationDocument::getOwnerType, "ADVERTISER")
				.eq(OcpxIntegrationDocument::getOwnerId, product.getAdvertiserId())
				.eq(OcpxIntegrationDocument::getStatus, "ACTIVE")
				.eq(OcpxIntegrationDocument::getScanStatus, "CLEAN")
				.orderByDesc(OcpxIntegrationDocument::getDocumentVersion)
				.last("LIMIT 100"))
			.stream()
			.map(document -> new AppLinkViews.DocumentOption(document.getId(), document.getDocumentType(),
					document.getFileName(), number(document.getDocumentVersion()), document.getStatus(),
					document.getScanStatus(), document.getId().equals(advertiser.getCurrentDocumentId())))
			.toList();
	}

	public AppLinkViews.ValidationReport validateUrl(AppLinkRequests.ValidateUrl request) {
		OcpxUrlPolicy.Result result = urlPolicy.validate(request.url());
		return validationReport(request.kind(), result);
	}

	@Transactional
	public AppLinkViews.Detail create(String idempotencyKey, AppLinkRequests.Create request) {
		String requestKey = validateIdempotencyKey(idempotencyKey);
		Structure structure = validateStructure(request.productId(), request.documentId(), request.linkType(),
				request.linkName(), request.linkUrlTemplate(), request.redirectEnabled(), request.deeplinkUrl(),
				request.downloadUrl(), request.description(), OcpxAccessScope.PermissionLevel.OPERATE);
		String requestHash = sha256(String.join("|", structure.product().getId(), structure.document().getId(),
				request.linkType(), normalizeName(request.linkName()), structure.template().normalizedUrl(),
				String.valueOf(structure.deeplink() == null ? null : structure.deeplink().normalizedUrl()),
				String.valueOf(structure.download() == null ? null : structure.download().normalizedUrl()),
				String.valueOf(request.redirectEnabled()), String.valueOf(trimToNull(request.description())),
				"DISABLED"));
		OcpxAppLink existing = appLinkMapper
			.selectOne(Wrappers.<OcpxAppLink>lambdaQuery().eq(OcpxAppLink::getCreateRequestKey, requestKey));
		if (existing != null) {
			if (!requestHash.equals(existing.getCreateRequestHash())) {
				throw conflict("OCPX_IDEMPOTENCY_CONFLICT：幂等键已用于不同的链接创建请求");
			}
			return detailView(existing);
		}
		ensureUnique(null, structure.product().getId(), request.linkName());
		OcpxAppLink link = new OcpxAppLink();
		link.setBizCode(id());
		link.setAdvertiserId(structure.product().getAdvertiserId());
		link.setProductId(structure.product().getId());
		link.setDocumentId(structure.document().getId());
		link.setDocumentVersion(number(structure.document().getDocumentVersion()));
		link.setLinkPlatform(platform(request.linkType()));
		link.setLinkType(request.linkType());
		link.setLinkName(request.linkName().trim());
		link.setNormalizedName(normalizeName(request.linkName()));
		applyUrls(link, structure);
		link.setDescription(trimToNull(request.description()));
		link.setLinkRemark(trimToNull(request.description()));
		// Creation never bypasses the separately authorized status transition.
		link.setStatus("DISABLED");
		link.setMappingStatus("READY");
		link.setConfigVersion(1L);
		link.setCreateRequestKey(requestKey);
		link.setCreateRequestHash(requestHash);
		link.setCreateBy(actor());
		link.setUpdateBy(actor());
		try {
			appLinkMapper.insert(link);
		}
		catch (DuplicateKeyException duplicate) {
			OcpxAppLink concurrent = appLinkMapper
				.selectOne(Wrappers.<OcpxAppLink>lambdaQuery().eq(OcpxAppLink::getCreateRequestKey, requestKey));
			if (concurrent != null && requestHash.equals(concurrent.getCreateRequestHash())) {
				return detailView(concurrent);
			}
			throw conflict("OCPX_APP_LINK_DUPLICATE：同一产品下链接名称已存在");
		}
		audit(link, "CREATE", 0, "已创建结构化 AppLink；URL 只记录主机和指纹", 0);
		return detailView(link);
	}

	@Transactional
	public AppLinkViews.Detail update(String id, AppLinkRequests.Update request) {
		OcpxAppLink link = lockLink(id, OcpxAccessScope.PermissionLevel.OPERATE);
		if (!"READY".equals(link.getMappingStatus())) {
			throw conflict("OCPX_APP_LINK_MAPPING_REQUIRED：存量链接需先完成 Product 与 URL 显式补映射");
		}
		long before = version(link);
		requireVersion(before, request.expectedVersion());
		if (!link.getProductId().equals(request.productId())) {
			throw conflict("OCPX_APP_LINK_PRODUCT_IMMUTABLE：已结构化链接不允许变更 Product");
		}
		String template = StringUtils.hasText(request.linkUrlTemplate()) ? request.linkUrlTemplate()
				: link.getLinkUrlTemplate();
		String deeplink = Boolean.TRUE.equals(request.clearDeeplink()) ? null
				: StringUtils.hasText(request.deeplinkUrl()) ? request.deeplinkUrl() : link.getDeeplinkUrl();
		String download = Boolean.TRUE.equals(request.clearDownload()) ? null
				: StringUtils.hasText(request.downloadUrl()) ? request.downloadUrl() : link.getDownloadUrl();
		Structure structure = validateStructure(request.productId(), request.documentId(), request.linkType(),
				request.linkName(), template, request.redirectEnabled(), deeplink, download, request.description(),
				OcpxAccessScope.PermissionLevel.OPERATE);
		ensureUnique(link.getId(), link.getProductId(), request.linkName());
		link.setDocumentId(structure.document().getId());
		link.setDocumentVersion(number(structure.document().getDocumentVersion()));
		link.setLinkPlatform(platform(request.linkType()));
		link.setLinkType(request.linkType());
		link.setLinkName(request.linkName().trim());
		link.setNormalizedName(normalizeName(request.linkName()));
		applyUrls(link, structure);
		link.setDescription(trimToNull(request.description()));
		link.setLinkRemark(trimToNull(request.description()));
		link.setConfigVersion(before + 1);
		link.setUpdateBy(actor());
		try {
			appLinkMapper.updateById(link);
		}
		catch (DuplicateKeyException duplicate) {
			throw conflict("OCPX_APP_LINK_DUPLICATE：同一产品下链接名称已存在");
		}
		int routes = configChangeService.appLinkChanged(id, "AppLink 结构化配置已变更");
		audit(link, "UPDATE", before, "已更新 AppLink；旧发布快照保持不变", routes);
		return detailView(link);
	}

	@Transactional
	public AppLinkViews.Detail completeLegacyMapping(String id, AppLinkRequests.CompleteLegacyMapping request) {
		OcpxAppLink link = lockLink(id, OcpxAccessScope.PermissionLevel.OPERATE);
		if ("READY".equals(link.getMappingStatus())) {
			throw conflict("OCPX_APP_LINK_ALREADY_MAPPED：链接已完成显式映射");
		}
		long before = version(link);
		requireVersion(before, request.expectedVersion());
		Structure structure = validateStructure(request.productId(), request.documentId(), request.linkType(),
				request.linkName(), request.linkUrlTemplate(), request.redirectEnabled(), request.deeplinkUrl(),
				request.downloadUrl(), request.description(), OcpxAccessScope.PermissionLevel.OPERATE);
		if (!link.getAdvertiserId().equals(structure.product().getAdvertiserId())) {
			throw new IllegalArgumentException("OCPX_LINK_RELATION_MISMATCH：Product 不属于存量链接原广告主");
		}
		ensureUnique(link.getId(), structure.product().getId(), request.linkName());
		link.setBizCode(StringUtils.hasText(link.getBizCode()) ? link.getBizCode() : id());
		link.setProductId(structure.product().getId());
		link.setDocumentId(structure.document().getId());
		link.setDocumentVersion(number(structure.document().getDocumentVersion()));
		link.setLinkPlatform(platform(request.linkType()));
		link.setLinkType(request.linkType());
		link.setLinkName(request.linkName().trim());
		link.setNormalizedName(normalizeName(request.linkName()));
		applyUrls(link, structure);
		link.setDescription(trimToNull(request.description()));
		link.setLinkRemark(trimToNull(request.description()));
		link.setStatus("DISABLED");
		link.setMappingStatus("READY");
		link.setConfigVersion(Math.max(1, before + 1));
		link.setUpdateBy(actor());
		try {
			appLinkMapper.updateById(link);
		}
		catch (DuplicateKeyException duplicate) {
			throw conflict("OCPX_APP_LINK_DUPLICATE：同一产品下链接名称已存在");
		}
		int routes = configChangeService.appLinkChanged(id, "存量 AppLink 已完成显式补映射");
		audit(link, "LEGACY_MAPPING_COMPLETED", before, "已由操作人显式确认 Product、资料和 URL", routes);
		return detailView(link);
	}

	@Transactional
	public AppLinkViews.Mutation changeStatus(String id, AppLinkRequests.StatusAction request) {
		OcpxAppLink link = lockLink(id, OcpxAccessScope.PermissionLevel.OPERATE);
		long before = version(link);
		requireVersion(before, request.expectedVersion());
		if (!"READY".equals(link.getMappingStatus())) {
			throw conflict("OCPX_APP_LINK_MAPPING_REQUIRED：待补映射链接不可启用");
		}
		if ("ARCHIVED".equals(link.getStatus())) {
			throw conflict("OCPX_APP_LINK_ARCHIVED：归档链接请先恢复");
		}
		if ("ACTIVE".equals(request.status())) {
			requireProduct(link.getProductId(), OcpxAccessScope.PermissionLevel.OPERATE, true);
			validateStoredUrls(link);
		}
		link.setStatus(request.status());
		link.setConfigVersion(before + 1);
		link.setUpdateBy(actor());
		appLinkMapper.updateById(link);
		int routes = configChangeService.appLinkChanged(id, "AppLink 状态已变更为 " + request.status());
		audit(link, "STATUS", before, "状态已变更为 " + request.status() + "；原因：" + sanitizeReason(request.reason()), routes);
		return mutation(link, routes);
	}

	@Transactional
	public AppLinkViews.Mutation archive(String id, AppLinkRequests.VersionedAction request) {
		OcpxAppLink link = lockLink(id, OcpxAccessScope.PermissionLevel.MANAGE);
		long before = version(link);
		requireVersion(before, request.expectedVersion());
		AppLinkViews.ReferenceSummary references = references(id);
		if (references.monitorCount() > 0 || references.deductionRuleCount() > 0) {
			throw conflict("OCPX_OBJECT_IN_USE：链接仍被 " + references.monitorCount() + " 条路由和 "
					+ references.deductionRuleCount() + " 条扣量规则引用");
		}
		if (!"ARCHIVED".equals(link.getStatus())) {
			link.setStatus("ARCHIVED");
			link.setArchivedBy(actor());
			link.setArchivedAt(LocalDateTime.now());
			link.setConfigVersion(before + 1);
			link.setUpdateBy(actor());
			appLinkMapper.updateById(link);
			audit(link, "ARCHIVE", before, "已归档 AppLink；原因：" + sanitizeReason(request.reason()), 0);
		}
		return mutation(link, 0);
	}

	@Transactional
	public AppLinkViews.Mutation restore(String id, AppLinkRequests.VersionedAction request) {
		OcpxAppLink link = lockLink(id, OcpxAccessScope.PermissionLevel.MANAGE);
		long before = version(link);
		requireVersion(before, request.expectedVersion());
		if (!"READY".equals(link.getMappingStatus())) {
			throw conflict("OCPX_APP_LINK_MAPPING_REQUIRED：待补映射链接不可恢复");
		}
		requireProduct(link.getProductId(), OcpxAccessScope.PermissionLevel.OPERATE, true);
		if ("ARCHIVED".equals(link.getStatus())) {
			link.setStatus("DISABLED");
			link.setArchivedBy(null);
			link.setArchivedAt(null);
			link.setConfigVersion(before + 1);
			link.setUpdateBy(actor());
			appLinkMapper.updateById(link);
			audit(link, "RESTORE", before, "已恢复为 DISABLED；原因：" + sanitizeReason(request.reason()), 0);
		}
		return mutation(link, 0);
	}

	private Structure validateStructure(String productId, String documentId, String linkType, String linkName,
			String templateUrl, Boolean redirectEnabled, String deeplinkUrl, String downloadUrl, String description,
			OcpxAccessScope.PermissionLevel level) {
		OcpxProduct product = requireProduct(productId, level, true);
		requireAdvertiser(product.getAdvertiserId(), level, true);
		OcpxIntegrationDocument document = requireDocument(product.getAdvertiserId(), documentId);
		OcpxUrlPolicy.Result template = requireSafeUrl(templateUrl, "上游链接模板");
		OcpxUrlPolicy.Result deeplink = StringUtils.hasText(deeplinkUrl) ? requireSafeUrl(deeplinkUrl, "直达链接") : null;
		OcpxUrlPolicy.Result download = StringUtils.hasText(downloadUrl) ? requireSafeUrl(downloadUrl, "下载地址") : null;
		if (Boolean.TRUE.equals(redirectEnabled) && deeplink == null && download == null) {
			throw new IllegalArgumentException("OCPX_REDIRECT_TARGET_REQUIRED：开启重定向时至少配置一个安全直达或下载地址");
		}
		if (!List.of("ANDROID", "IOS", "OTHER").contains(linkType)) {
			throw new IllegalArgumentException("链接类型无效");
		}
		if (!StringUtils.hasText(linkName) || linkName.trim().length() > 64) {
			throw new IllegalArgumentException("链接名称必须为 1 到 64 个字符");
		}
		if (description != null && description.trim().length() > 500) {
			throw new IllegalArgumentException("链接描述不能超过 500 个字符");
		}
		return new Structure(product, document, template, deeplink, download, Boolean.TRUE.equals(redirectEnabled));
	}

	private void applyUrls(OcpxAppLink link, Structure structure) {
		link.setLinkUrlTemplate(structure.template().normalizedUrl());
		link.setLinkUrl(structure.template().normalizedUrl());
		link.setTemplateHost(structure.template().host());
		link.setTemplateFingerprint(structure.template().fingerprint());
		link.setTemplateMacroSet(writeMacros(structure.template().macros()));
		link.setRedirectEnabled(structure.redirectEnabled());
		link.setDeeplinkUrl(structure.deeplink() == null ? null : structure.deeplink().normalizedUrl());
		link.setDeeplink(structure.deeplink() == null ? null : structure.deeplink().normalizedUrl());
		link.setDownloadUrl(structure.download() == null ? null : structure.download().normalizedUrl());
	}

	private void validateStoredUrls(OcpxAppLink link) {
		requireSafeUrl(link.getLinkUrlTemplate(), "上游链接模板");
		if (StringUtils.hasText(link.getDeeplinkUrl())) {
			requireSafeUrl(link.getDeeplinkUrl(), "直达链接");
		}
		if (StringUtils.hasText(link.getDownloadUrl())) {
			requireSafeUrl(link.getDownloadUrl(), "下载地址");
		}
		if (Boolean.TRUE.equals(link.getRedirectEnabled()) && !StringUtils.hasText(link.getDeeplinkUrl())
				&& !StringUtils.hasText(link.getDownloadUrl())) {
			throw new IllegalArgumentException("OCPX_REDIRECT_TARGET_REQUIRED：重定向缺少安全目标");
		}
	}

	private OcpxUrlPolicy.Result requireSafeUrl(String value, String label) {
		OcpxUrlPolicy.Result result = urlPolicy.validate(value);
		if (!"PASS".equals(result.status())) {
			throw new IllegalArgumentException(
					"OCPX_LINK_URL_INVALID：" + label + " 校验失败（" + String.join(",", result.issues()) + "）");
		}
		return result;
	}

	private OcpxProduct requireProduct(String id, OcpxAccessScope.PermissionLevel level, boolean active) {
		OcpxProduct product = productMapper.selectById(id);
		if (product == null) {
			throw new IllegalArgumentException("产品不存在或无权访问");
		}
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, product.getAdvertiserId(), level);
		if (active && !"ACTIVE".equals(product.getStatus())) {
			throw new IllegalArgumentException("产品未启用");
		}
		return product;
	}

	private OcpxAdvertiser requireAdvertiser(String id, OcpxAccessScope.PermissionLevel level, boolean active) {
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, id, level);
		OcpxAdvertiser advertiser = advertiserMapper.selectById(id);
		if (advertiser == null || active && !"ACTIVE".equals(advertiser.getStatus())) {
			throw new IllegalArgumentException("广告主不存在、未启用或无权访问");
		}
		return advertiser;
	}

	private OcpxIntegrationDocument requireDocument(String advertiserId, String documentId) {
		OcpxIntegrationDocument document = documentMapper.selectById(documentId);
		if (document == null || !"ADVERTISER".equals(document.getOwnerType())
				|| !advertiserId.equals(document.getOwnerId()) || !"ACTIVE".equals(document.getStatus())
				|| !"CLEAN".equals(document.getScanStatus()) || number(document.getDocumentVersion()) < 1) {
			throw new IllegalArgumentException("接入资料不存在、未通过安全检查或与广告主不一致");
		}
		return document;
	}

	private OcpxAppLink requireLink(String id, OcpxAccessScope.PermissionLevel level) {
		OcpxAppLink link = appLinkMapper.selectById(id);
		if (link == null) {
			throw new IllegalArgumentException("链接不存在或无权访问");
		}
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, link.getAdvertiserId(), level);
		return link;
	}

	private OcpxAppLink lockLink(String id, OcpxAccessScope.PermissionLevel level) {
		OcpxAppLink link = appLinkMapper
			.selectOne(Wrappers.<OcpxAppLink>lambdaQuery().eq(OcpxAppLink::getId, id).last("FOR UPDATE"));
		if (link == null) {
			throw new IllegalArgumentException("链接不存在或无权访问");
		}
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, link.getAdvertiserId(), level);
		return link;
	}

	private void ensureUnique(String id, String productId, String name) {
		long duplicates = appLinkMapper.selectCount(Wrappers.<OcpxAppLink>lambdaQuery()
			.eq(OcpxAppLink::getProductId, productId)
			.eq(OcpxAppLink::getNormalizedName, normalizeName(name))
			.ne(OcpxAppLink::getStatus, "ARCHIVED")
			.ne(StringUtils.hasText(id), OcpxAppLink::getId, id));
		if (duplicates > 0) {
			throw conflict("OCPX_APP_LINK_DUPLICATE：同一产品下链接名称已存在");
		}
	}

	private AppLinkViews.ReferenceSummary references(String id) {
		long monitors = monitorMapper
			.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery().eq(OcpxMonitorLink::getAppLinkId, id));
		long published = monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getAppLinkId, id)
			.eq(OcpxMonitorLink::getRouteStatus, "PUBLISHED"));
		long deductions = deductionMapper
			.selectCount(Wrappers.<OcpxDeductionRule>lambdaQuery().eq(OcpxDeductionRule::getAppLinkId, id));
		long republish = monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery()
			.eq(OcpxMonitorLink::getAppLinkId, id)
			.eq(OcpxMonitorLink::getRouteStatus, "PUBLISHED")
			.eq(OcpxMonitorLink::getRepublishRequired, true));
		return new AppLinkViews.ReferenceSummary(monitors, published, deductions, republish);
	}

	private AppLinkViews.ListItem listItem(OcpxAppLink link) {
		OcpxProduct product = StringUtils.hasText(link.getProductId()) ? productMapper.selectById(link.getProductId())
				: null;
		OcpxAdvertiser advertiser = advertiserMapper.selectById(link.getAdvertiserId());
		AppLinkViews.ReferenceSummary references = references(link.getId());
		return new AppLinkViews.ListItem(link.getId(), link.getBizCode(), link.getAdvertiserId(),
				advertiser == null ? "-" : advertiser.getAdvertiserName(), link.getProductId(),
				product == null ? null : product.getCode(), product == null ? null : product.getName(),
				link.getDocumentId(), number(link.getDocumentVersion()), link.getLinkName(), link.getLinkType(),
				Boolean.TRUE.equals(link.getRedirectEnabled()), masked(link, link.getLinkUrlTemplate()),
				masked(link, link.getDeeplinkUrl()), masked(link, link.getDownloadUrl()), link.getTemplateHost(),
				macros(link.getTemplateMacroSet()), link.getStatus(), link.getMappingStatus(), version(link),
				references.republishRequiredCount(), link.getCreateTime(), link.getUpdateTime());
	}

	private AppLinkViews.Detail detailView(OcpxAppLink link) {
		OcpxProduct product = StringUtils.hasText(link.getProductId()) ? productMapper.selectById(link.getProductId())
				: null;
		OcpxAdvertiser advertiser = advertiserMapper.selectById(link.getAdvertiserId());
		return new AppLinkViews.Detail(link.getId(), link.getBizCode(), link.getAdvertiserId(),
				advertiser == null ? "-" : advertiser.getAdvertiserName(), link.getProductId(),
				product == null ? null : product.getCode(), product == null ? null : product.getName(),
				link.getDocumentId(), number(link.getDocumentVersion()), link.getLinkName(), link.getLinkType(),
				Boolean.TRUE.equals(link.getRedirectEnabled()), masked(link, link.getLinkUrlTemplate()),
				masked(link, link.getDeeplinkUrl()), masked(link, link.getDownloadUrl()),
				StringUtils.hasText(link.getLinkUrlTemplate()), StringUtils.hasText(link.getDeeplinkUrl()),
				StringUtils.hasText(link.getDownloadUrl()), link.getTemplateHost(), macros(link.getTemplateMacroSet()),
				"READY".equals(link.getMappingStatus()) ? "PASS" : "PENDING_REVIEW", link.getDescription(),
				link.getStatus(), link.getMappingStatus(), version(link), references(link.getId()),
				link.getArchivedAt(), link.getCreateTime(), link.getUpdateTime());
	}

	private String masked(OcpxAppLink link, String value) {
		if (!StringUtils.hasText(value)) {
			return "LEGACY_PENDING_PRODUCT".equals(link.getMappingStatus()) && StringUtils.hasText(link.getLinkUrl())
					? "历史地址（待重新录入并校验）" : null;
		}
		OcpxUrlPolicy.Result result = urlPolicy.validate(value);
		return StringUtils.hasText(result.maskedUrl()) ? result.maskedUrl() : "已保存（待安全复核）";
	}

	private AppLinkViews.ValidationReport validationReport(String kind, OcpxUrlPolicy.Result result) {
		return new AppLinkViews.ValidationReport(kind, result.status(), false, result.maskedUrl(), result.host(),
				result.fingerprint(), result.macros(), result.issues());
	}

	private AppLinkViews.Mutation mutation(OcpxAppLink link, int routes) {
		return new AppLinkViews.Mutation(link.getId(), link.getStatus(), link.getMappingStatus(), version(link),
				routes);
	}

	private void audit(OcpxAppLink link, String action, long before, String summary, int routes) {
		OcpxAppLinkChangeAudit audit = new OcpxAppLinkChangeAudit();
		audit.setAppLinkId(link.getId());
		audit.setAdvertiserId(link.getAdvertiserId());
		audit.setProductId(link.getProductId());
		audit.setAction(action);
		audit.setBeforeConfigVersion(before);
		audit.setAfterConfigVersion(version(link));
		audit.setMappingStatus(link.getMappingStatus());
		audit.setTemplateHost(link.getTemplateHost());
		audit.setTemplateFingerprint(link.getTemplateFingerprint());
		audit.setAffectedRouteCount(routes);
		audit.setActor(actor());
		audit.setChangeSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);
		auditMapper.insert(audit);
	}

	private List<String> macros(String value) {
		if (!StringUtils.hasText(value)) {
			return List.of();
		}
		try {
			return objectMapper.readValue(value, STRING_LIST);
		}
		catch (Exception error) {
			return List.of();
		}
	}

	private String writeMacros(List<String> values) {
		try {
			return objectMapper.writeValueAsString(values);
		}
		catch (Exception error) {
			throw new IllegalStateException("无法序列化 URL 宏集合", error);
		}
	}

	private int platform(String linkType) {
		return switch (linkType) {
			case "ANDROID" -> 1;
			case "IOS" -> 2;
			default -> 4;
		};
	}

	private String normalizeName(String value) {
		return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private String sanitizeReason(String value) {
		if (!StringUtils.hasText(value)) {
			return "未填写原因";
		}
		String sanitized = value.trim()
			.replaceAll("[\\r\\n\\t]+", " ")
			.replaceAll("(?i)(https?://|secret|password|token|api[-_]?key|credential)[^\\s]*", "[REDACTED]")
			.replaceAll("[A-Za-z0-9+/=_-]{32,}", "[REDACTED]");
		return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
	}

	private String validateIdempotencyKey(String value) {
		if (!StringUtils.hasText(value) || !value.matches("^[A-Za-z0-9_-]{16,64}$")) {
			throw new IllegalArgumentException("Idempotency-Key 必须为 16 到 64 位字母、数字、下划线或连字符");
		}
		return value;
	}

	private void requireVersion(long actual, long expected) {
		if (actual != expected) {
			throw conflict("OCPX_VERSION_CONFLICT：链接已被其他操作修改，请刷新后重试");
		}
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

	private String actor() {
		String actor = SecurityUtils.getUserName();
		return StringUtils.hasText(actor) ? actor : "unknown";
	}

	private long version(OcpxAppLink link) {
		return number(link.getConfigVersion());
	}

	private long number(Long value) {
		return value == null ? 0 : value;
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
			throw new IllegalStateException("无法计算请求摘要", error);
		}
	}

	private record Structure(OcpxProduct product, OcpxIntegrationDocument document, OcpxUrlPolicy.Result template,
			OcpxUrlPolicy.Result deeplink, OcpxUrlPolicy.Result download, boolean redirectEnabled) {
	}

}
