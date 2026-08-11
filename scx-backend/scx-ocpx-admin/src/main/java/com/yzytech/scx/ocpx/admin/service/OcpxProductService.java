package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.ProductRequests;
import com.yzytech.scx.ocpx.admin.dto.ProductViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.entity.OcpxProductChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxProductEventPolicy;
import com.yzytech.scx.ocpx.admin.entity.OcpxProductLogoObject;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductEventPolicyMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductLogoObjectMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.service.StorageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcpxProductService {

	private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

	private static final int MAX_LOGO_DIMENSION = 2048;

	private final OcpxProductMapper productMapper;

	private final OcpxProductEventPolicyMapper policyMapper;

	private final OcpxProductLogoObjectMapper logoMapper;

	private final OcpxProductChangeAuditMapper auditMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxAppLinkMapper appLinkMapper;

	private final OcpxAccessScope accessScope;

	private final OcpxConfigChangeService configChangeService;

	private final StorageUploadService storageUploadService;

	private final JdbcTemplate jdbcTemplate;

	public IPage<ProductViews.ListItem> list(Page<OcpxProduct> page, String keyword, String advertiserId, String status,
			Boolean archived) {
		OcpxAccessScope.Restriction restriction = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.ADVERTISER, OcpxAccessScope.PermissionLevel.VIEW);
		if (StringUtils.hasText(advertiserId)) {
			accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, advertiserId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			Page<ProductViews.ListItem> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
			empty.setRecords(List.of());
			return empty;
		}
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		return productMapper
			.selectPage(page,
					Wrappers.<OcpxProduct>lambdaQuery()
						.in(!restriction.unrestricted(), OcpxProduct::getAdvertiserId, restriction.ids())
						.eq(StringUtils.hasText(advertiserId), OcpxProduct::getAdvertiserId, advertiserId)
						.and(StringUtils.hasText(normalizedKeyword),
								query -> query.like(OcpxProduct::getName, normalizedKeyword)
									.or()
									.like(OcpxProduct::getCode, normalizedKeyword)
									.or()
									.like(OcpxProduct::getDescription, normalizedKeyword))
						.eq(Boolean.TRUE.equals(archived), OcpxProduct::getStatus, "ARCHIVED")
						.ne(!Boolean.TRUE.equals(archived), OcpxProduct::getStatus, "ARCHIVED")
						.eq(StringUtils.hasText(status) && !Boolean.TRUE.equals(archived), OcpxProduct::getStatus,
								status)
						.orderByDesc(OcpxProduct::getUpdateTime))
			.convert(this::listItem);
	}

	public ProductViews.Detail detail(String id) {
		return detailView(requireProduct(id, OcpxAccessScope.PermissionLevel.VIEW));
	}

	public List<ProductViews.AdvertiserOption> advertiserOptions(String keyword) {
		OcpxAccessScope.Restriction restriction = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.ADVERTISER, OcpxAccessScope.PermissionLevel.VIEW);
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			return List.of();
		}
		return advertiserMapper
			.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery()
				.in(!restriction.unrestricted(), OcpxAdvertiser::getId, restriction.ids())
				.eq(OcpxAdvertiser::getStatus, "ACTIVE")
				.and(StringUtils.hasText(keyword),
						query -> query.like(OcpxAdvertiser::getAdvertiserName, keyword.trim())
							.or()
							.like(OcpxAdvertiser::getAdvertiserCode, keyword.trim()))
				.orderByAsc(OcpxAdvertiser::getAdvertiserName)
				.last("LIMIT 100"))
			.stream()
			.map(item -> new ProductViews.AdvertiserOption(item.getId(), item.getAdvertiserCode(),
					item.getAdvertiserName()))
			.toList();
	}

	public List<ProductViews.EventOption> eventOptions(String keyword) {
		List<Object> args = new ArrayList<>();
		String keywordSql = "";
		if (StringUtils.hasText(keyword)) {
			keywordSql = " AND (event_code LIKE ? OR event_name LIKE ?)";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		return jdbcTemplate.query("""
				SELECT id,event_code,event_name FROM scx_ocpx_event_definition
				WHERE deleted=0 AND status='ACTIVE' AND category='STANDARD' AND channel_id=''
				  AND mapping_state='CANONICAL' AND direction IN ('DOWNSTREAM_DELIVERY','BIDIRECTIONAL')
				""" + keywordSql + " ORDER BY sort_order,event_code LIMIT 100",
				(rs, rowNum) -> new ProductViews.EventOption(rs.getString(1), rs.getString(2), rs.getString(3)),
				args.toArray());
	}

	@Transactional
	public ProductViews.Detail create(String idempotencyKey, ProductRequests.Create request) {
		String requestKey = validateIdempotencyKey(idempotencyKey);
		OcpxAdvertiser advertiser = requireActiveAdvertiser(request.advertiserId(),
				OcpxAccessScope.PermissionLevel.OPERATE);
		List<ProductRequests.EventPolicy> policies = validatedPolicies(request.eventPolicies());
		String requestHash = createRequestHash(request, policies);
		OcpxProduct existing = productMapper
			.selectOne(Wrappers.<OcpxProduct>lambdaQuery().eq(OcpxProduct::getCreateRequestKey, requestKey));
		if (existing != null) {
			if (!requestHash.equals(existing.getCreateRequestHash())) {
				throw conflict("OCPX_IDEMPOTENCY_CONFLICT：幂等键已用于不同的创建请求");
			}
			return detailView(existing);
		}
		ensureUnique(null, advertiser.getId(), request.code(), request.name());

		OcpxProduct product = new OcpxProduct();
		product.setAdvertiserId(advertiser.getId());
		product.setCode(normalizeCode(request.code()));
		product.setName(request.name().trim());
		product.setNormalizedName(normalizeName(request.name()));
		product.setPackageName(trimToNull(request.packageName()));
		product.setDescription(trimToNull(request.description()));
		product.setStatus(request.status());
		product.setConfigVersion(1L);
		product.setEventPolicyRevision(1L);
		product.setCreateRequestKey(requestKey);
		product.setCreateRequestHash(requestHash);
		product.setCreateBy(actor());
		product.setUpdateBy(actor());
		try {
			productMapper.insert(product);
			syncPolicies(product.getId(), policies, actor());
		}
		catch (DuplicateKeyException duplicate) {
			OcpxProduct concurrent = productMapper
				.selectOne(Wrappers.<OcpxProduct>lambdaQuery().eq(OcpxProduct::getCreateRequestKey, requestKey));
			if (concurrent != null && requestHash.equals(concurrent.getCreateRequestHash())) {
				return detailView(concurrent);
			}
			throw conflict("OCPX_PRODUCT_DUPLICATE：同一广告主下产品编码或名称已存在");
		}
		audit(product, "CREATE", 0, "已创建产品主数据和事件默认策略", 0);
		return detailView(product);
	}

	@Transactional
	public ProductViews.Detail update(String id, ProductRequests.Update request) {
		OcpxProduct product = lockProduct(id, OcpxAccessScope.PermissionLevel.OPERATE);
		long before = version(product);
		requireVersion(before, request.expectedVersion());
		List<ProductRequests.EventPolicy> policies = validatedPolicies(request.eventPolicies());
		ensureUnique(product.getId(), product.getAdvertiserId(), product.getCode(), request.name());
		boolean policyChanged = !policySignature(product.getId()).equals(policySignature(policies));
		product.setName(request.name().trim());
		product.setNormalizedName(normalizeName(request.name()));
		product.setPackageName(trimToNull(request.packageName()));
		product.setDescription(trimToNull(request.description()));
		product.setStatus(request.status());
		product.setConfigVersion(before + 1);
		if (policyChanged) {
			product.setEventPolicyRevision(number(product.getEventPolicyRevision()) + 1);
			syncPolicies(product.getId(), policies, actor());
		}
		product.setUpdateBy(actor());
		try {
			productMapper.updateById(product);
		}
		catch (DuplicateKeyException duplicate) {
			throw conflict("OCPX_PRODUCT_DUPLICATE：同一广告主下产品名称已存在");
		}
		int routes = configChangeService.productChanged(id, policyChanged ? "产品事件默认策略已变更" : "产品主数据已变更");
		audit(product, "UPDATE", before, policyChanged ? "已更新产品主数据和事件默认策略" : "已更新产品主数据", routes);
		return detailView(product);
	}

	@Transactional
	public ProductViews.Mutation archive(String id, ProductRequests.VersionedAction request) {
		OcpxProduct product = lockProduct(id, OcpxAccessScope.PermissionLevel.OPERATE);
		long before = version(product);
		requireVersion(before, request.expectedVersion());
		ProductViews.ReferenceSummary references = references(id);
		if (references.appLinkCount() > 0 || references.routeCount() > 0) {
			throw conflict("OCPX_OBJECT_IN_USE：产品仍有 " + references.appLinkCount() + " 个应用链接和 " + references.routeCount()
					+ " 条路由引用");
		}
		if (!"ARCHIVED".equals(product.getStatus())) {
			product.setStatus("ARCHIVED");
			product.setArchivedBy(actor());
			product.setArchivedAt(LocalDateTime.now());
			product.setConfigVersion(before + 1);
			product.setUpdateBy(actor());
			productMapper.updateById(product);
			audit(product, "ARCHIVE", before, "已归档产品；原因：" + sanitizeReason(request.reason()), 0);
		}
		return mutation(product, 0);
	}

	@Transactional
	public ProductViews.Mutation restore(String id, ProductRequests.VersionedAction request) {
		OcpxProduct product = lockProduct(id, OcpxAccessScope.PermissionLevel.OPERATE);
		long before = version(product);
		requireVersion(before, request.expectedVersion());
		requireActiveAdvertiser(product.getAdvertiserId(), OcpxAccessScope.PermissionLevel.OPERATE);
		if ("ARCHIVED".equals(product.getStatus())) {
			product.setStatus("ACTIVE");
			product.setArchivedBy(null);
			product.setArchivedAt(null);
			product.setConfigVersion(before + 1);
			product.setUpdateBy(actor());
			productMapper.updateById(product);
			audit(product, "RESTORE", before, "已恢复产品；原因：" + sanitizeReason(request.reason()), 0);
		}
		return mutation(product, 0);
	}

	@Transactional
	public ProductViews.Detail uploadLogo(String id, long expectedVersion, MultipartFile file) {
		OcpxProduct visible = requireProduct(id, OcpxAccessScope.PermissionLevel.OPERATE);
		requireVersion(version(visible), expectedVersion);
		ValidatedLogo logo = validateLogo(file);
		ManagedStorageObject stored = null;
		boolean rollbackCleanupRegistered = false;
		try {
			stored = storageUploadService.uploadManaged(new ByteArrayInputStream(logo.bytes()), logo.fileName(),
					logo.contentType(), logo.bytes().length);
			rollbackCleanupRegistered = cleanupOnRollback(stored, id);
			OcpxProduct product = lockProduct(id, OcpxAccessScope.PermissionLevel.OPERATE);
			long before = version(product);
			requireVersion(before, expectedVersion);
			String oldLogoId = product.getLogoObjectId();
			OcpxProductLogoObject metadata = logoMetadata(stored, logo);
			logoMapper.insert(metadata);
			product.setLogoObjectId(metadata.getId());
			product.setConfigVersion(before + 1);
			product.setUpdateBy(actor());
			productMapper.updateById(product);
			retireAfterCommit(oldLogoId, product.getId());
			int routes = configChangeService.productChanged(id, "产品Logo已变更");
			audit(product, "LOGO_UPDATE", before, "已更新受管Logo引用", routes);
			return detailView(product);
		}
		catch (RuntimeException error) {
			if (stored != null && !rollbackCleanupRegistered) {
				deleteQuietly(stored, id, "new-logo-compensation");
			}
			throw error;
		}
	}

	@Transactional
	public ProductViews.Detail removeLogo(String id, long expectedVersion) {
		OcpxProduct product = lockProduct(id, OcpxAccessScope.PermissionLevel.OPERATE);
		long before = version(product);
		requireVersion(before, expectedVersion);
		String oldLogoId = product.getLogoObjectId();
		if (!StringUtils.hasText(oldLogoId)) {
			return detailView(product);
		}
		product.setLogoObjectId(null);
		product.setConfigVersion(before + 1);
		product.setUpdateBy(actor());
		productMapper.updateById(product);
		retireAfterCommit(oldLogoId, product.getId());
		int routes = configChangeService.productChanged(id, "产品Logo已移除");
		audit(product, "LOGO_REMOVE", before, "已移除受管Logo引用", routes);
		return detailView(product);
	}

	public LogoDownload logo(String id) {
		OcpxProduct product = requireProduct(id, OcpxAccessScope.PermissionLevel.VIEW);
		OcpxProductLogoObject logo = StringUtils.hasText(product.getLogoObjectId())
				? logoMapper.selectById(product.getLogoObjectId()) : null;
		if (logo == null || !"ACTIVE".equals(logo.getStatus())) {
			throw new IllegalArgumentException("产品Logo不存在或无权访问");
		}
		ManagedStorageObject object = managedObject(logo);
		return new LogoDownload(logo.getFileName(), logo.getContentType(), logo.getFileSize(),
				storageUploadService.open(object));
	}

	private ProductViews.ListItem listItem(OcpxProduct product) {
		long policies = policyMapper.selectCount(Wrappers.<OcpxProductEventPolicy>lambdaQuery()
			.eq(OcpxProductEventPolicy::getProductId, product.getId())
			.eq(OcpxProductEventPolicy::getStatus, "ACTIVE"));
		ProductViews.ReferenceSummary references = references(product.getId());
		return new ProductViews.ListItem(product.getId(), product.getAdvertiserId(),
				advertiserName(product.getAdvertiserId()), product.getCode(), product.getName(),
				product.getPackageName(), product.getDescription(), product.getStatus(), version(product),
				number(product.getEventPolicyRevision()), StringUtils.hasText(product.getLogoObjectId()),
				logoUrl(product), policies, references.appLinkCount(), republishRequiredCount(product.getId()),
				product.getCreateTime(), product.getUpdateTime());
	}

	private ProductViews.Detail detailView(OcpxProduct product) {
		return new ProductViews.Detail(product.getId(), product.getAdvertiserId(),
				advertiserName(product.getAdvertiserId()), product.getCode(), product.getName(),
				product.getPackageName(), product.getDescription(), product.getStatus(), version(product),
				number(product.getEventPolicyRevision()), StringUtils.hasText(product.getLogoObjectId()),
				logoUrl(product), policyViews(product.getId()), references(product.getId()), product.getArchivedAt(),
				product.getCreateTime(), product.getUpdateTime());
	}

	private List<ProductViews.EventPolicy> policyViews(String productId) {
		return jdbcTemplate.query("""
				SELECT p.event_definition_id,e.event_code,e.event_name,p.daily_limit,p.version
				FROM scx_ocpx_product_event_policy p
				JOIN scx_ocpx_event_definition e ON BINARY e.id=BINARY p.event_definition_id
				WHERE p.product_id=? AND p.status='ACTIVE'
				ORDER BY e.sort_order,e.event_code
				""", (rs, rowNum) -> new ProductViews.EventPolicy(rs.getString(1), rs.getString(2), rs.getString(3),
				rs.getInt(4), rs.getLong(5)), productId);
	}

	private List<ProductRequests.EventPolicy> validatedPolicies(List<ProductRequests.EventPolicy> requested) {
		List<ProductRequests.EventPolicy> policies = ProductRequests.safePolicies(requested);
		Set<String> ids = new HashSet<>();
		for (ProductRequests.EventPolicy policy : policies) {
			if (!ids.add(policy.eventDefinitionId())) {
				throw conflict("OCPX_PRODUCT_EVENT_DUPLICATE：同一事件只能配置一次");
			}
			Long count = jdbcTemplate.queryForObject("""
					SELECT COUNT(*) FROM scx_ocpx_event_definition
					WHERE id=? AND deleted=0 AND status='ACTIVE' AND category='STANDARD' AND channel_id=''
					  AND mapping_state='CANONICAL' AND direction IN ('DOWNSTREAM_DELIVERY','BIDIRECTIONAL')
					""", Long.class, policy.eventDefinitionId());
			if (count == null || count != 1) {
				throw new IllegalArgumentException("事件默认策略只能引用已启用的全局转化事件");
			}
		}
		return policies;
	}

	private void syncPolicies(String productId, List<ProductRequests.EventPolicy> requested, String operator) {
		Map<String, OcpxProductEventPolicy> existing = new HashMap<>();
		policyMapper
			.selectList(
					Wrappers.<OcpxProductEventPolicy>lambdaQuery().eq(OcpxProductEventPolicy::getProductId, productId))
			.forEach(item -> existing.put(item.getEventDefinitionId(), item));
		Set<String> retained = new HashSet<>();
		for (ProductRequests.EventPolicy input : requested) {
			retained.add(input.eventDefinitionId());
			OcpxProductEventPolicy policy = existing.get(input.eventDefinitionId());
			if (policy == null) {
				policy = new OcpxProductEventPolicy();
				policy.setProductId(productId);
				policy.setEventDefinitionId(input.eventDefinitionId());
				policy.setDailyLimit(input.dailyLimit());
				policy.setStatus("ACTIVE");
				policy.setVersion(1L);
				policy.setCreateBy(operator);
				policy.setUpdateBy(operator);
				policyMapper.insert(policy);
			}
			else if (!"ACTIVE".equals(policy.getStatus()) || !input.dailyLimit().equals(policy.getDailyLimit())) {
				policy.setDailyLimit(input.dailyLimit());
				policy.setStatus("ACTIVE");
				policy.setVersion(number(policy.getVersion()) + 1);
				policy.setUpdateBy(operator);
				policyMapper.updateById(policy);
			}
		}
		for (OcpxProductEventPolicy policy : existing.values()) {
			if (!retained.contains(policy.getEventDefinitionId()) && "ACTIVE".equals(policy.getStatus())) {
				policy.setStatus("DISABLED");
				policy.setVersion(number(policy.getVersion()) + 1);
				policy.setUpdateBy(operator);
				policyMapper.updateById(policy);
			}
		}
	}

	private String policySignature(String productId) {
		return policyMapper
			.selectList(Wrappers.<OcpxProductEventPolicy>lambdaQuery()
				.eq(OcpxProductEventPolicy::getProductId, productId)
				.eq(OcpxProductEventPolicy::getStatus, "ACTIVE"))
			.stream()
			.map(item -> item.getEventDefinitionId() + ":" + item.getDailyLimit())
			.sorted()
			.reduce((left, right) -> left + "|" + right)
			.orElse("");
	}

	private String policySignature(List<ProductRequests.EventPolicy> policies) {
		return policies.stream()
			.map(item -> item.eventDefinitionId() + ":" + item.dailyLimit())
			.sorted()
			.reduce((left, right) -> left + "|" + right)
			.orElse("");
	}

	private void ensureUnique(String id, String advertiserId, String code, String name) {
		long duplicates = productMapper.selectCount(Wrappers.<OcpxProduct>lambdaQuery()
			.eq(OcpxProduct::getAdvertiserId, advertiserId)
			.and(query -> query.eq(OcpxProduct::getCode, normalizeCode(code))
				.or()
				.eq(OcpxProduct::getNormalizedName, normalizeName(name)))
			.ne(StringUtils.hasText(id), OcpxProduct::getId, id));
		if (duplicates > 0) {
			throw conflict("OCPX_PRODUCT_DUPLICATE：同一广告主下产品编码或名称已存在");
		}
	}

	private OcpxAdvertiser requireActiveAdvertiser(String advertiserId, OcpxAccessScope.PermissionLevel level) {
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, advertiserId, level);
		OcpxAdvertiser advertiser = advertiserMapper.selectById(advertiserId);
		if (advertiser == null || !"ACTIVE".equals(advertiser.getStatus())) {
			throw new IllegalArgumentException("广告主不存在、未启用或无权访问");
		}
		return advertiser;
	}

	private OcpxProduct requireProduct(String id, OcpxAccessScope.PermissionLevel level) {
		OcpxProduct product = productMapper.selectById(id);
		if (product == null) {
			throw new IllegalArgumentException("产品不存在或无权访问");
		}
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, product.getAdvertiserId(), level);
		return product;
	}

	private OcpxProduct lockProduct(String id, OcpxAccessScope.PermissionLevel level) {
		OcpxProduct product = productMapper
			.selectOne(Wrappers.<OcpxProduct>lambdaQuery().eq(OcpxProduct::getId, id).last("FOR UPDATE"));
		if (product == null) {
			throw new IllegalArgumentException("产品不存在或无权访问");
		}
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, product.getAdvertiserId(), level);
		return product;
	}

	private ProductViews.ReferenceSummary references(String productId) {
		long appLinks = appLinkMapper.selectCount(Wrappers.<OcpxAppLink>lambdaQuery()
			.eq(OcpxAppLink::getProductId, productId)
			.eq(OcpxAppLink::getDeleted, false));
		Long routes = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_monitor_link m
				JOIN scx_ocpx_app_link l ON l.id=m.app_link_id AND l.deleted=0
				WHERE m.deleted=0 AND l.product_id=?
				""", Long.class, productId);
		return new ProductViews.ReferenceSummary(appLinks, routes == null ? 0 : routes);
	}

	private long republishRequiredCount(String productId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_monitor_link m
				JOIN scx_ocpx_app_link l ON l.id=m.app_link_id AND l.deleted=0
				WHERE m.deleted=0 AND m.route_status='PUBLISHED' AND m.republish_required=1 AND l.product_id=?
				""", Long.class, productId);
		return count == null ? 0 : count;
	}

	private ProductViews.Mutation mutation(OcpxProduct product, int routes) {
		return new ProductViews.Mutation(product.getId(), product.getStatus(), version(product),
				number(product.getEventPolicyRevision()), routes);
	}

	private ValidatedLogo validateLogo(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() > MAX_LOGO_BYTES) {
			throw new IllegalArgumentException("Logo 必须为不超过 2MB 的 PNG 或 JPEG 图片");
		}
		try {
			byte[] bytes = file.getBytes();
			String contentType = detectImageContentType(bytes);
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > MAX_LOGO_DIMENSION
					|| image.getHeight() > MAX_LOGO_DIMENSION) {
				throw new IllegalArgumentException("Logo 尺寸必须在 1 到 2048 像素之间");
			}
			String extension = "image/png".equals(contentType) ? "png" : "jpg";
			return new ValidatedLogo(bytes, "product-logo." + extension, contentType, image.getWidth(),
					image.getHeight(), sha256(bytes));
		}
		catch (IllegalArgumentException error) {
			throw error;
		}
		catch (Exception error) {
			throw new IllegalArgumentException("Logo 图片无法读取", error);
		}
	}

	private String detectImageContentType(byte[] bytes) {
		if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
				&& bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
			return "image/png";
		}
		if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
			return "image/jpeg";
		}
		throw new IllegalArgumentException("Logo 仅支持 PNG 或 JPEG 图片");
	}

	private OcpxProductLogoObject logoMetadata(ManagedStorageObject stored, ValidatedLogo logo) {
		OcpxProductLogoObject metadata = new OcpxProductLogoObject();
		metadata.setStorageConfigId(stored.storageConfigId());
		metadata.setStorageProvider(stored.provider());
		metadata.setObjectKey(stored.objectKey());
		metadata.setFileName(logo.fileName());
		metadata.setContentType(logo.contentType());
		metadata.setFileSize((long) logo.bytes().length);
		metadata.setSha256(logo.sha256());
		metadata.setImageWidth(logo.width());
		metadata.setImageHeight(logo.height());
		metadata.setStatus("ACTIVE");
		metadata.setCreatedBy(actor());
		return metadata;
	}

	private void retireAfterCommit(String logoId, String productId) {
		if (!StringUtils.hasText(logoId)) {
			return;
		}
		OcpxProductLogoObject old = logoMapper.selectById(logoId);
		if (old == null || !"ACTIVE".equals(old.getStatus())) {
			return;
		}
		old.setStatus("RETIRED");
		old.setRetiredAt(LocalDateTime.now());
		logoMapper.updateById(old);
		Runnable cleanup = () -> deleteQuietly(managedObject(old), productId, "retired-logo-cleanup");
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					cleanup.run();
				}
			});
		}
		else {
			cleanup.run();
		}
	}

	private boolean cleanupOnRollback(ManagedStorageObject object, String productId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			return false;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != TransactionSynchronization.STATUS_COMMITTED) {
					deleteQuietly(object, productId, "new-logo-rollback");
				}
			}
		});
		return true;
	}

	private ManagedStorageObject managedObject(OcpxProductLogoObject logo) {
		return new ManagedStorageObject(logo.getStorageConfigId(), logo.getStorageProvider(), logo.getObjectKey(),
				null);
	}

	private void deleteQuietly(ManagedStorageObject object, String productId, String operation) {
		try {
			storageUploadService.delete(object);
		}
		catch (RuntimeException error) {
			log.warn("Product managed Logo cleanup failed: productId={}, operation={}", productId, operation);
		}
	}

	private void audit(OcpxProduct product, String action, long beforeVersion, String summary, int routes) {
		OcpxProductChangeAudit audit = new OcpxProductChangeAudit();
		audit.setProductId(product.getId());
		audit.setAdvertiserId(product.getAdvertiserId());
		audit.setAction(action);
		audit.setBeforeConfigVersion(beforeVersion);
		audit.setAfterConfigVersion(version(product));
		audit.setEventPolicyRevision(number(product.getEventPolicyRevision()));
		audit.setAffectedAppLinkCount(Math.toIntExact(references(product.getId()).appLinkCount()));
		audit.setAffectedRouteCount(routes);
		audit.setActor(actor());
		audit.setChangeSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);
		auditMapper.insert(audit);
	}

	private String advertiserName(String advertiserId) {
		OcpxAdvertiser advertiser = advertiserMapper.selectById(advertiserId);
		return advertiser == null ? "-" : advertiser.getAdvertiserName();
	}

	private String logoUrl(OcpxProduct product) {
		return StringUtils.hasText(product.getLogoObjectId()) ? "/ocpx/products/" + product.getId() + "/logo" : null;
	}

	private String createRequestHash(ProductRequests.Create request, List<ProductRequests.EventPolicy> policies) {
		String canonical = String.join("|", request.advertiserId(), normalizeCode(request.code()),
				normalizeName(request.name()), String.valueOf(trimToNull(request.packageName())),
				String.valueOf(trimToNull(request.description())), request.status(), policySignature(policies));
		return sha256(canonical.getBytes(StandardCharsets.UTF_8));
	}

	private String validateIdempotencyKey(String value) {
		if (!StringUtils.hasText(value) || !value.matches("^[A-Za-z0-9_-]{16,64}$")) {
			throw new IllegalArgumentException("Idempotency-Key 必须为 16 到 64 位字母、数字、下划线或连字符");
		}
		return value;
	}

	private String normalizeCode(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
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
			.replaceAll("(?i)\\b(secret|password|token|api[-_]?key|credential)\\b\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
			.replaceAll("[A-Za-z0-9+/=_-]{32,}", "[REDACTED]");
		return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
	}

	private void requireVersion(long actual, long expected) {
		if (actual != expected) {
			throw conflict("OCPX_VERSION_CONFLICT：产品已被其他操作修改，请刷新后重试");
		}
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

	private String actor() {
		String actor = SecurityUtils.getUserName();
		return StringUtils.hasText(actor) ? actor : "unknown";
	}

	private long version(OcpxProduct product) {
		return number(product.getConfigVersion());
	}

	private long number(Long value) {
		return value == null ? 0 : value;
	}

	private String sha256(byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法计算内容摘要", error);
		}
	}

	private record ValidatedLogo(byte[] bytes, String fileName, String contentType, int width, int height,
			String sha256) {
	}

	public record LogoDownload(String fileName, String contentType, long size, InputStream inputStream) {
	}

}
