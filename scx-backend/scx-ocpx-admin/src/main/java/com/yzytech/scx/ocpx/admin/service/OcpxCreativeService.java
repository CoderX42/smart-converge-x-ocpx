package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.admin.dto.CreativeRequests;
import com.yzytech.scx.ocpx.admin.dto.CreativeViews;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxCreativeMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMaterialMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxCreative;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.entity.SysMaterial;
import com.yzytech.scx.system.api.service.StorageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcpxCreativeService {

	private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;

	private static final int MAX_EDGE = 8192;

	private static final long MAX_PIXELS = 40_000_000L;

	private final OcpxCreativeMapper creativeMapper;

	private final OcpxMaterialMapper materialMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxPromotionLinkMapper linkMapper;

	private final StorageUploadService storageUploadService;

	private final JdbcTemplate jdbcTemplate;

	private final AdvertiserScope advertiserScope;

	public IPage<CreativeViews.Item> list(Page<OcpxCreative> page, String keyword, String advertiserId,
			Integer status, String promotionLinkId) {
		String scopedAdvertiserId = advertiserScope.constrainSelfOwned(advertiserId);
		Set<String> idsByLink = idsByLink(promotionLinkId, scopedAdvertiserId);
		IPage<OcpxCreative> result = creativeMapper.selectPage(page, Wrappers.<OcpxCreative>lambdaQuery()
			.eq(StringUtils.hasText(scopedAdvertiserId), OcpxCreative::getAdvertiserId, scopedAdvertiserId)
			.eq(status != null, OcpxCreative::getStatus, status)
			.and(StringUtils.hasText(keyword), q -> q.like(OcpxCreative::getId, keyword)
				.or()
				.like(OcpxCreative::getCreativeName, keyword))
			.in(StringUtils.hasText(promotionLinkId), OcpxCreative::getId,
				idsByLink.isEmpty() ? Set.of("__NONE__") : idsByLink)
			.orderByDesc(OcpxCreative::getUpdateTime));
		return result.convert(this::view);
	}

	public CreativeViews.Item detail(String id) {
		OcpxCreative creative = require(id);
		advertiserScope.requireSelfOwnedAdvertiser(creative.getAdvertiserId());
		return view(creative);
	}

	public List<CreativeViews.Asset> assets(String advertiserId, String keyword) {
		String owner = requireAdvertiser(advertiserScope.constrainSelfOwned(advertiserId));
		return materialMapper.selectList(Wrappers.<SysMaterial>lambdaQuery()
			.eq(SysMaterial::getShopId, owner)
			.eq(SysMaterial::getType, "1")
			.like(StringUtils.hasText(keyword), SysMaterial::getName, keyword)
			.orderByDesc(SysMaterial::getCreateTime)
			.last("LIMIT 200"))
			.stream()
			.map(item -> new CreativeViews.Asset(item.getId(), item.getName(), item.getUrl(), item.getFileSize(),
					item.getCreateTime()))
			.toList();
	}

	public CreativeViews.UploadedAsset upload(String requestedAdvertiserId, MultipartFile file) {
		String advertiserId = requireAdvertiser(advertiserScope.constrainSelfOwned(requestedAdvertiserId));
		if (file == null || file.isEmpty()) {
			throw new BootBusinessException("请选择图片素材");
		}
		String fileName = cleanFileName(file.getOriginalFilename());
		byte[] content;
		try {
			content = file.getBytes();
		}
		catch (IOException exception) {
			throw new BootBusinessException("读取图片素材失败");
		}
		ImageAsset image = inspect(content, fileName);
		ManagedStorageObject stored = storageUploadService.uploadManaged(new ByteArrayInputStream(content), fileName,
				image.mimeType(), content.length);
		SysMaterial material = new SysMaterial();
		material.setName(fileName);
		material.setType("1");
		material.setUrl(stored.url());
		material.setShopId(advertiserId);
		material.setFileSize((long) content.length);
		material.setDelFlag("0");
		material.setCreateBy(operatorUserId());
		material.setCreateTime(LocalDateTime.now());
		try {
			materialMapper.insert(material);
		}
		catch (RuntimeException exception) {
			try {
				storageUploadService.delete(stored);
			}
			catch (RuntimeException cleanupException) {
				log.error("creative asset cleanup failed objectKey:{}", stored.objectKey(), cleanupException);
				exception.addSuppressed(cleanupException);
			}
			throw exception;
		}
		List<String> duplicateIds = creativeMapper
			.selectList(Wrappers.<OcpxCreative>lambdaQuery()
				.eq(OcpxCreative::getAdvertiserId, advertiserId)
				.eq(OcpxCreative::getChecksumSha256, image.checksum()))
			.stream()
			.map(OcpxCreative::getId)
			.toList();
		return new CreativeViews.UploadedAsset(material.getId(), fileName, stored.url(), image.mimeType(),
				(long) content.length, image.width(), image.height(), image.checksum(), duplicateIds);
	}

	@Transactional
	public CreativeViews.Item create(CreativeRequests.Save request) {
		String advertiserId = requireAdvertiser(advertiserScope.constrainSelfOwned(request.getAdvertiserId()));
		ImageMaterial material = requireMaterial(advertiserId, request.getSourceMaterialId());
		assertUniqueName(advertiserId, request.getCreativeName(), null);
		List<String> linkIds = normalizeLinks(request.getPromotionLinkIds());
		validateLinks(advertiserId, linkIds);
		OcpxCreative creative = new OcpxCreative();
		apply(creative, advertiserId, request, material);
		creative.setDeleted(false);
		creativeMapper.insert(creative);
		replaceBindings(creative, linkIds);
		return detail(creative.getId());
	}

	@Transactional
	public CreativeViews.Item update(String id, CreativeRequests.Save request) {
		OcpxCreative current = require(id);
		advertiserScope.requireSelfOwnedAdvertiser(current.getAdvertiserId());
		String requestedOwner = advertiserScope.constrainSelfOwned(request.getAdvertiserId());
		if (!current.getAdvertiserId().equals(requestedOwner)) {
			throw new BootBusinessException("创意创建后不能更换广告主");
		}
		lockCreative(id);
		ImageMaterial material = requireMaterial(current.getAdvertiserId(), request.getSourceMaterialId());
		assertUniqueName(current.getAdvertiserId(), request.getCreativeName(), id);
		List<String> linkIds = normalizeLinks(request.getPromotionLinkIds());
		validateLinks(current.getAdvertiserId(), linkIds);
		OcpxCreative update = new OcpxCreative();
		update.setId(id);
		apply(update, current.getAdvertiserId(), request, material);
		creativeMapper.updateById(update);
		replaceBindings(current, linkIds);
		return detail(id);
	}

	public List<CreativeViews.LinkOption> bindings(String id) {
		OcpxCreative creative = require(id);
		advertiserScope.requireSelfOwnedAdvertiser(creative.getAdvertiserId());
		return bindingOptions(id);
	}

	@Transactional
	public List<CreativeViews.LinkOption> replaceBindings(String id, CreativeRequests.Bindings request) {
		OcpxCreative creative = require(id);
		advertiserScope.requireSelfOwnedAdvertiser(creative.getAdvertiserId());
		lockCreative(id);
		List<String> linkIds = normalizeLinks(request.getPromotionLinkIds());
		validateLinks(creative.getAdvertiserId(), linkIds);
		replaceBindings(creative, linkIds);
		return bindingOptions(id);
	}

	@Transactional
	public void delete(String id) {
		OcpxCreative creative = require(id);
		advertiserScope.requireSelfOwnedAdvertiser(creative.getAdvertiserId());
		lockCreative(id);
		jdbcTemplate.update("DELETE FROM scx_ocpx_creative_promotion_link WHERE creative_id=?", id);
		creativeMapper.deleteById(id);
	}

	private void apply(OcpxCreative creative, String advertiserId, CreativeRequests.Save request,
			ImageMaterial material) {
		creative.setAdvertiserId(advertiserId);
		creative.setCreativeName(request.getCreativeName().trim());
		creative.setCreativeType(1);
		creative.setAssetUrl(material.url());
		creative.setSourceMaterialId(material.id());
		creative.setFileName(material.fileName());
		creative.setMimeType(material.image().mimeType());
		creative.setFileSize(material.image().fileSize());
		creative.setWidth(material.image().width());
		creative.setHeight(material.image().height());
		creative.setChecksumSha256(material.image().checksum());
		creative.setStatus(request.getStatus());
		creative.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
	}

	private ImageMaterial requireMaterial(String advertiserId, String materialId) {
		SysMaterial material = materialMapper.selectOne(Wrappers.<SysMaterial>lambdaQuery()
			.eq(SysMaterial::getId, materialId)
			.eq(SysMaterial::getShopId, advertiserId)
			.eq(SysMaterial::getType, "1")
			.eq(SysMaterial::getDelFlag, "0"));
		if (material == null) {
			throw new BootBusinessException("图片素材不存在或不属于当前广告主");
		}
		ManagedStorageObject object = storageUploadService.resolve(material.getUrl());
		byte[] content;
		try (InputStream input = storageUploadService.open(object)) {
			content = input.readNBytes(MAX_FILE_BYTES + 1);
		}
		catch (IOException | RuntimeException exception) {
			throw new BootBusinessException("读取图片素材失败");
		}
		ImageAsset image = inspect(content, cleanFileName(material.getName()));
		return new ImageMaterial(material.getId(), material.getName(), material.getUrl(), image);
	}

	private ImageAsset inspect(byte[] content, String fileName) {
		if (content.length == 0 || content.length > MAX_FILE_BYTES) {
			throw new BootBusinessException("图片大小必须大于 0 且不超过 20MB");
		}
		try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
			Iterator<ImageReader> readers = stream == null ? null : ImageIO.getImageReaders(stream);
			if (readers == null || !readers.hasNext()) {
				throw new BootBusinessException("图片文件头或内容无效");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(stream, true, true);
				String format = normalizeFormat(reader.getFormatName());
				validateExtension(fileName, format);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE
						|| (long) width * height > MAX_PIXELS) {
					throw new BootBusinessException("图片最长边不能超过 8192px，且总像素不能超过 4000 万");
				}
				return new ImageAsset(mimeType(format), (long) content.length, width, height, sha256(content));
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException exception) {
			throw new BootBusinessException("图片文件头或内容无效");
		}
	}

	private String normalizeFormat(String value) {
		String format = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if ("JPG".equals(format)) {
			return "JPEG";
		}
		if (!Set.of("JPEG", "PNG", "WEBP").contains(format)) {
			throw new BootBusinessException("仅支持 JPG、JPEG、PNG、WEBP 图片");
		}
		return format;
	}

	private void validateExtension(String fileName, String format) {
		int separator = fileName.lastIndexOf('.');
		String extension = separator < 0 ? "" : fileName.substring(separator + 1).toUpperCase(Locale.ROOT);
		boolean matches = switch (format) {
			case "JPEG" -> Set.of("JPG", "JPEG").contains(extension);
			case "PNG" -> "PNG".equals(extension);
			case "WEBP" -> "WEBP".equals(extension);
			default -> false;
		};
		if (!matches) {
			throw new BootBusinessException("图片扩展名与真实文件格式不一致");
		}
	}

	private String mimeType(String format) {
		return switch (format) {
			case "JPEG" -> "image/jpeg";
			case "PNG" -> "image/png";
			case "WEBP" -> "image/webp";
			default -> throw new BootBusinessException("不支持的图片格式");
		};
	}

	private void validateLinks(String advertiserId, List<String> linkIds) {
		if (linkIds.isEmpty()) {
			return;
		}
		long count = linkMapper.selectCount(Wrappers.<OcpxPromotionLink>lambdaQuery()
			.eq(OcpxPromotionLink::getAdvertiserId, advertiserId)
			.in(OcpxPromotionLink::getId, linkIds));
		if (count != linkIds.size()) {
			throw new BootBusinessException("推广链接不存在或不属于当前广告主");
		}
	}

	private void replaceBindings(OcpxCreative creative, List<String> targetIds) {
		Set<String> currentIds = new HashSet<>(jdbcTemplate.queryForList(
				"SELECT promotion_link_id FROM scx_ocpx_creative_promotion_link WHERE creative_id=?", String.class,
				creative.getId()));
		Set<String> target = new LinkedHashSet<>(targetIds);
		Set<String> removed = new HashSet<>(currentIds);
		removed.removeAll(target);
		if (!removed.isEmpty()) {
			String placeholders = String.join(",", java.util.Collections.nCopies(removed.size(), "?"));
			List<Object> args = new ArrayList<>();
			args.add(creative.getId());
			args.addAll(removed);
			jdbcTemplate.update("DELETE FROM scx_ocpx_creative_promotion_link WHERE creative_id=? AND promotion_link_id IN ("
					+ placeholders + ")", args.toArray());
		}
		List<String> added = target.stream().filter(id -> !currentIds.contains(id)).toList();
		if (!added.isEmpty()) {
			String operator = operatorUserId();
			jdbcTemplate.batchUpdate("""
					INSERT INTO scx_ocpx_creative_promotion_link
					(advertiser_id,creative_id,promotion_link_id,create_by,create_time)
					VALUES (?,?,?,?,CURRENT_TIMESTAMP(3))
					""", added, added.size(), (statement, linkId) -> {
				statement.setString(1, creative.getAdvertiserId());
				statement.setString(2, creative.getId());
				statement.setString(3, linkId);
				statement.setString(4, operator);
			});
		}
	}

	private List<CreativeViews.LinkOption> bindingOptions(String creativeId) {
		return jdbcTemplate.query("""
				SELECT l.id,l.link_name
				FROM scx_ocpx_creative_promotion_link b
				JOIN scx_ocpx_promotion_link l ON l.id=b.promotion_link_id AND l.is_delete=0
				WHERE b.creative_id=? ORDER BY l.link_name
				""", (rs, rowNum) -> new CreativeViews.LinkOption(rs.getString(1), rs.getString(2)), creativeId);
	}

	private Set<String> idsByLink(String promotionLinkId, String advertiserId) {
		if (!StringUtils.hasText(promotionLinkId)) {
			return Set.of();
		}
		StringBuilder sql = new StringBuilder(
				"SELECT creative_id FROM scx_ocpx_creative_promotion_link WHERE promotion_link_id=?");
		List<Object> args = new ArrayList<>();
		args.add(promotionLinkId.trim());
		if (StringUtils.hasText(advertiserId)) {
			sql.append(" AND advertiser_id=?");
			args.add(advertiserId);
		}
		return new HashSet<>(jdbcTemplate.queryForList(sql.toString(), String.class, args.toArray()));
	}

	private CreativeViews.Item view(OcpxCreative creative) {
		OcpxAdvertiser advertiser = advertiserMapper.selectById(creative.getAdvertiserId());
		return new CreativeViews.Item(creative.getId(), creative.getAdvertiserId(),
				advertiser == null ? null : advertiser.getAdvertiserName(), creative.getCreativeName(),
				creative.getCreativeType(), creative.getAssetUrl(), creative.getSourceMaterialId(), creative.getFileName(),
				creative.getMimeType(), creative.getFileSize(), creative.getWidth(), creative.getHeight(),
				creative.getChecksumSha256(), creative.getStatus(), creative.getRemark(), bindingOptions(creative.getId()),
				creative.getCreateTime(), creative.getUpdateTime());
	}

	private OcpxCreative require(String id) {
		OcpxCreative creative = creativeMapper.selectById(id);
		if (creative == null) {
			throw new BootBusinessException("创意不存在");
		}
		return creative;
	}

	private String requireAdvertiser(String advertiserId) {
		if (!StringUtils.hasText(advertiserId) || advertiserMapper.selectById(advertiserId) == null) {
			throw new BootBusinessException("广告主不存在");
		}
		return advertiserId.trim();
	}

	private void assertUniqueName(String advertiserId, String name, String excludedId) {
		long count = creativeMapper.selectCount(Wrappers.<OcpxCreative>lambdaQuery()
			.eq(OcpxCreative::getAdvertiserId, advertiserId)
			.eq(OcpxCreative::getCreativeName, name.trim())
			.ne(StringUtils.hasText(excludedId), OcpxCreative::getId, excludedId));
		if (count > 0) {
			throw new BootBusinessException("同一广告主下创意名称不能重复");
		}
	}

	private void lockCreative(String id) {
		jdbcTemplate.queryForObject("SELECT id FROM scx_ocpx_creative WHERE id=? AND is_delete=0 FOR UPDATE",
				String.class, id);
	}

	private List<String> normalizeLinks(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
	}

	private String cleanFileName(String value) {
		if (!StringUtils.hasText(value)) {
			throw new BootBusinessException("图片文件名不能为空");
		}
		String normalized = value.replace('\\', '/');
		String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
		if (!StringUtils.hasText(fileName) || fileName.length() > 255) {
			throw new BootBusinessException("图片文件名无效");
		}
		return fileName;
	}

	private String operatorUserId() {
		BootUser user = SecurityUtils.getUser();
		return user == null ? null : user.getUserId();
	}

	private String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private record ImageAsset(String mimeType, Long fileSize, Integer width, Integer height, String checksum) {
	}

	private record ImageMaterial(String id, String fileName, String url, ImageAsset image) {
	}

}
