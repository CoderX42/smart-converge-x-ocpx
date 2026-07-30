package com.yzytech.scx.delivery.service;

import com.yzytech.scx.delivery.entity.*;
import com.yzytech.scx.delivery.mapper.*;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.service.StorageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MasterDataService {

	private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("csv", "doc", "docx", "json", "md", "pdf", "txt",
			"xlsx", "yaml", "yml");

	private static final Set<String> DOCUMENT_STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED", "RETIRED");

	private static final long MAX_DOCUMENT_SIZE = 20L * 1024 * 1024;

	private static final Set<String> PARTNER_STATUSES = Set.of("ACTIVE", "DISABLED");

	private final MediaScopeService scopeService;

	private final PartnerMapper partnerMapper;

	private final UpstreamCustomerMapper customerMapper;

	private final ProductMapper productMapper;

	private final IntegrationDocumentMapper documentMapper;

	private final MediaChannelMapper channelMapper;

	private final StorageUploadService storageUploadService;

	public boolean readable(String mediaId) {
		return scopeService.canRead(mediaId);
	}

	public boolean writable(String mediaId) {
		return scopeService.canWrite(mediaId);
	}

	@Transactional
	public boolean savePartner(Partner value) {
		if (value == null)
			throw new IllegalArgumentException("合作方信息不能为空");
		requireWrite(value.getMediaId());
		if (value.getId() != null) {
			Partner existing = partnerMapper.selectById(value.getId());
			requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
			if (value.getVersion() == null || value.getVersion() < 0)
				throw new IllegalArgumentException("合作方版本无效，请刷新后重试");
		}
		validatePartner(value);
		defaults(value);
		return value.getId() == null ? partnerMapper.insert(value) == 1 : partnerMapper.updateById(value) == 1;
	}

	@Transactional
	public boolean deletePartner(String id, Integer version) {
		Partner partner = partnerMapper.selectForUpdate(id);
		if (partner == null || !scopeService.canWrite(partner.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		if (!Objects.equals(partner.getVersion(), version))
			throw new IllegalStateException("合作方已被其他用户修改，请刷新后重试");
		if (partnerMapper.countReferences(id) > 0)
			throw new IllegalStateException("该合作方仍被产品、追踪链接或历史报表引用，请先解除引用或将合作方设为停用");
		return partnerMapper.deleteById(id) == 1;
	}

	@Transactional
	public boolean saveCustomer(UpstreamCustomer value) {
		requireWrite(value.getMediaId());
		if (value.getId() != null) {
			UpstreamCustomer existing = customerMapper.selectById(value.getId());
			requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
		}
		if (value.getStatus() == null)
			value.setStatus("ACTIVE");
		if (value.getVersion() == null)
			value.setVersion(0);
		return value.getId() == null ? customerMapper.insert(value) == 1 : customerMapper.updateById(value) == 1;
	}

	@Transactional
	public boolean saveProduct(Product value) {
		requireWrite(value.getMediaId());
		Product existing = null;
		if (value.getId() != null) {
			existing = productMapper.selectById(value.getId());
			requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
		}
		Partner partner = partnerMapper.selectById(value.getPartnerId());
		if (partner == null || !value.getMediaId().equals(partner.getMediaId()))
			throw new IllegalArgumentException("合作方不在当前媒体范围");
		// New business data belongs to Partner. Keep the retired customer
		// reference only on records that already carried it before this merge.
		value.setCustomerId(existing == null ? null : existing.getCustomerId());
		defaults(value);
		return value.getId() == null ? productMapper.insert(value) == 1 : productMapper.updateById(value) == 1;
	}

	@Transactional
	public boolean saveDocument(IntegrationDocument value) {
		return saveDocument(value, null) != null;
	}

	@Transactional
	public IntegrationDocument saveDocument(IntegrationDocument value, MultipartFile file) {
		if (value == null)
			throw new IllegalArgumentException("文档信息不能为空");
		requireWrite(value.getMediaId());
		IntegrationDocument existing = null;
		if (value.getId() != null) {
			existing = documentMapper.selectForUpdate(value.getId());
			requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
			if (!Objects.equals(existing.getVersion(), value.getVersion()))
				throw new IllegalStateException("文档已被其他用户修改，请刷新后重试");
		}
		value.setName(value.getName() == null ? null : value.getName().trim());
		if (!StringUtils.hasText(value.getName()) || value.getName().length() > 128)
			throw new IllegalArgumentException("文档名称不能为空且不能超过 128 个字符");
		if (value.getDescription() != null) {
			value.setDescription(value.getDescription().trim());
			if (value.getDescription().length() > 500)
				throw new IllegalArgumentException("文档说明不能超过 500 个字符");
		}
		if (!StringUtils.hasText(value.getType()) || !IntegrationDocumentType.isValid(value.getType()))
			throw new IllegalArgumentException("不支持的文档类型: " + value.getType());
		if (value.getStatus() != null && !DOCUMENT_STATUSES.contains(value.getStatus()))
			throw new IllegalArgumentException("不支持的文档状态: " + value.getStatus());

		boolean uploadedSource = "OBJECT".equals(value.getStorageType());
		boolean externalSource = "EXTERNAL".equals(value.getStorageType());
		if (uploadedSource == externalSource)
			throw new IllegalArgumentException("请选择上传文件或外部链接");

		ManagedStorageObject newObject = null;
		ManagedStorageObject oldObject = null;
		boolean sourceChanged = false;
		try {
			if (file != null && !file.isEmpty()) {
				if (!uploadedSource)
					throw new IllegalArgumentException("上传文件与所选文档来源不一致");
				if (existing != null && "OBJECT".equals(existing.getStorageType()))
					oldObject = managedObject(existing);
				byte[] content = validateAndReadDocument(file);
				newObject = storageUploadService.uploadManaged(new ByteArrayInputStream(content),
						cleanDocumentFileName(file), documentContentType(cleanDocumentFileName(file)), content.length);
				applyManagedFile(value, newObject, file, content);
				sourceChanged = true;
			}
			else if (uploadedSource) {
				if (existing != null && "OBJECT".equals(existing.getStorageType())) {
					copyManagedFile(existing, value);
				}
				else if (StringUtils.hasText(value.getObjectKey())) {
					// Compatibility for clients using the historical upload-then-JSON
					// flow. Reads and deletes still fail closed until this URL can be
					// matched to a persisted storage configuration.
					value.setObjectKey(value.getObjectKey().trim());
					value.setExternalUrl(null);
				}
				else {
					throw new IllegalArgumentException("请选择需要上传的文档文件");
				}
			}
			else {
				validateExternalUrl(value.getExternalUrl());
				value.setExternalUrl(value.getExternalUrl().trim());
				clearManagedFile(value);
				sourceChanged = existing == null || !"EXTERNAL".equals(existing.getStorageType())
						|| !Objects.equals(existing.getExternalUrl(), value.getExternalUrl());
				if (sourceChanged && existing != null && "OBJECT".equals(existing.getStorageType()))
					oldObject = managedObject(existing);
			}

			if (value.getStatus() == null)
				value.setStatus("DRAFT");
			if (existing == null) {
				value.setDocumentVersion(1);
				value.setVersion(0);
				if (documentMapper.insert(value) != 1)
					throw new IllegalStateException("文档保存失败");
			}
			else {
				value.setDocumentVersion(
						sourceChanged ? existing.getDocumentVersion() + 1 : existing.getDocumentVersion());
				if (documentMapper.updateById(value) != 1)
					throw new IllegalStateException("文档已被其他用户修改，请刷新后重试");
			}
			if (existing != null && sourceChanged && oldObject != null) {
				storageUploadService.delete(oldObject);
			}
			return value;
		}
		catch (RuntimeException exception) {
			if (newObject != null) {
				try {
					storageUploadService.delete(newObject);
				}
				catch (RuntimeException cleanupFailure) {
					exception.addSuppressed(cleanupFailure);
				}
			}
			throw exception;
		}
	}

	public String uploadDocument(String mediaId, MultipartFile file) {
		requireWrite(mediaId);
		byte[] content = validateAndReadDocument(file);
		return storageUploadService
			.uploadManaged(new ByteArrayInputStream(content), cleanDocumentFileName(file),
					documentContentType(cleanDocumentFileName(file)), content.length)
			.url();
	}

	public DocumentContent openDocument(String id) {
		IntegrationDocument document = documentMapper.selectById(id);
		if (document == null || !scopeService.canRead(document.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		if (!"OBJECT".equals(document.getStorageType()))
			throw new IllegalArgumentException("外部链接文档不支持站内预览");
		ManagedStorageObject object = managedObject(document);
		if (object == null)
			throw new IllegalStateException("文档存储引用不完整");
		return new DocumentContent(document, storageUploadService.open(object));
	}

	@Transactional
	public boolean deleteDocument(String id, Integer version) {
		IntegrationDocument document = documentMapper.selectForUpdate(id);
		if (document == null || !scopeService.canWrite(document.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		if (!Objects.equals(document.getVersion(), version))
			throw new IllegalStateException("文档已被其他用户修改，请刷新后重试");
		long referenceCount = documentMapper.countReferences(id);
		if (referenceCount > 0)
			throw new IllegalStateException("该文档仍被渠道、追踪链接或历史报表引用，请先解除引用或将文档设为已退役");
		ManagedStorageObject object = managedObject(document);
		if (object != null)
			storageUploadService.delete(object);
		return documentMapper.deleteById(id) == 1;
	}

	@Transactional
	public boolean saveChannel(MediaChannel value) {
		requireWrite(value.getMediaId());
		if (value.getId() != null) {
			MediaChannel existing = channelMapper.selectById(value.getId());
			requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
		}
		if (StringUtils.hasText(value.getDocumentId())) {
			IntegrationDocument document = documentMapper.selectById(value.getDocumentId());
			if (document == null || !value.getMediaId().equals(document.getMediaId()))
				throw new IllegalArgumentException("接入文档不在当前媒体范围");
		}
		defaults(value);
		return value.getId() == null ? channelMapper.insert(value) == 1 : channelMapper.updateById(value) == 1;
	}

	@Transactional
	public boolean deleteChannel(String id, Integer version) {
		MediaChannel channel = channelMapper.selectForUpdate(id);
		if (channel == null || !scopeService.canWrite(channel.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		if (!Objects.equals(channel.getVersion(), version))
			throw new IllegalStateException("投放渠道已被其他用户修改，请刷新后重试");
		if (channelMapper.countReferences(id) > 0)
			throw new IllegalStateException("该投放渠道仍被渠道用户、媒体投放账户、监测任务或历史报表引用，请先解除引用或将渠道设为停用");
		return channelMapper.deleteById(id) == 1;
	}

	public void validateExternalUrl(String value) {
		if (!StringUtils.hasText(value) || value.length() > 1000)
			throw new IllegalArgumentException("外部链接不能为空且不能超过 1000 个字符");
		try {
			URI uri = URI.create(value.trim());
			if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null
					|| uri.getHost().isBlank()) {
				throw new IllegalArgumentException("外部链接必须是合法的 HTTP 或 HTTPS 地址，且不能携带用户名密码");
			}
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalArgumentException("URL 格式无效");
		}
	}

	@Deprecated
	public void validateSandboxUrl(String value) {
		validateExternalUrl(value);
	}

	private byte[] validateAndReadDocument(MultipartFile file) {
		if (file == null || file.isEmpty())
			throw new IllegalArgumentException("请选择需要上传的文档");
		if (file.getSize() > MAX_DOCUMENT_SIZE)
			throw new IllegalArgumentException("文档大小不能超过 20MB");
		String fileName = cleanDocumentFileName(file);
		int extensionIndex = fileName.lastIndexOf('.');
		String extension = extensionIndex < 1 ? "" : fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
		if (!DOCUMENT_EXTENSIONS.contains(extension))
			throw new IllegalArgumentException("仅支持 PDF、Office、文本、CSV、JSON 和 YAML 文档");
		try {
			byte[] content = file.getBytes();
			if (content.length == 0)
				throw new IllegalArgumentException("请选择需要上传的文档");
			if (content.length > MAX_DOCUMENT_SIZE)
				throw new IllegalArgumentException("文档大小不能超过 20MB");
			return content;
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("读取上传文档失败", exception);
		}
	}

	private String cleanDocumentFileName(MultipartFile file) {
		String fileName = StringUtils
			.cleanPath(file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
		if (!StringUtils.hasText(fileName) || fileName.length() > 255 || fileName.contains("..")
				|| fileName.contains("/") || fileName.contains("\\")
				|| fileName.chars().anyMatch(Character::isISOControl))
			throw new IllegalArgumentException("文件名无效");
		return fileName;
	}

	private String documentContentType(String fileName) {
		String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		return switch (extension) {
			case "pdf" -> "application/pdf";
			case "doc" -> "application/msword";
			case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
			case "csv" -> "text/csv;charset=UTF-8";
			case "txt" -> "text/plain;charset=UTF-8";
			case "md" -> "text/markdown;charset=UTF-8";
			case "json" -> "application/json";
			case "yaml", "yml" -> "application/yaml";
			default -> "application/octet-stream";
		};
	}

	private void applyManagedFile(IntegrationDocument value, ManagedStorageObject object, MultipartFile file,
			byte[] content) {
		value.setStorageType("OBJECT");
		value.setObjectKey(object.url());
		value.setExternalUrl(null);
		value.setOriginalFileName(cleanDocumentFileName(file));
		value.setMimeType(documentContentType(value.getOriginalFileName()));
		value.setFileSize((long) content.length);
		value.setStorageConfigId(object.storageConfigId());
		value.setStorageProvider(object.provider());
		value.setStorageObjectKey(object.objectKey());
		value.setContentHash(sha256(content));
	}

	private void copyManagedFile(IntegrationDocument source, IntegrationDocument target) {
		target.setObjectKey(source.getObjectKey());
		target.setExternalUrl(null);
		target.setOriginalFileName(source.getOriginalFileName());
		target.setMimeType(source.getMimeType());
		target.setFileSize(source.getFileSize());
		target.setStorageConfigId(source.getStorageConfigId());
		target.setStorageProvider(source.getStorageProvider());
		target.setStorageObjectKey(source.getStorageObjectKey());
		target.setContentHash(source.getContentHash());
	}

	private void clearManagedFile(IntegrationDocument value) {
		value.setObjectKey(null);
		value.setOriginalFileName(null);
		value.setMimeType(null);
		value.setFileSize(null);
		value.setStorageConfigId(null);
		value.setStorageProvider(null);
		value.setStorageObjectKey(null);
		value.setContentHash(null);
	}

	private ManagedStorageObject managedObject(IntegrationDocument document) {
		if (document == null || !"OBJECT".equals(document.getStorageType())
				|| !StringUtils.hasText(document.getObjectKey()))
			return null;
		if (StringUtils.hasText(document.getStorageConfigId()) && StringUtils.hasText(document.getStorageProvider())
				&& StringUtils.hasText(document.getStorageObjectKey())) {
			return new ManagedStorageObject(document.getStorageConfigId(), document.getStorageProvider(),
					document.getStorageObjectKey(), document.getObjectKey());
		}
		return storageUploadService.resolve(document.getObjectKey());
	}

	private String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("系统不支持 SHA-256", exception);
		}
	}

	public record DocumentContent(IntegrationDocument document, InputStream stream) {
	}

	private void requireWrite(String mediaId) {
		if (!scopeService.canWrite(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

	private void requireSameMedia(String existingMediaId, String requestedMediaId) {
		if (existingMediaId == null || !existingMediaId.equals(requestedMediaId)
				|| !scopeService.canWrite(existingMediaId)) {
			throw new IllegalArgumentException("资源不存在");
		}
	}

	private void defaults(Partner value) {
		if (value.getStatus() == null)
			value.setStatus("ACTIVE");
		if (value.getType() == null)
			value.setType("UPSTREAM");
		if (value.getVersion() == null)
			value.setVersion(0);
	}

	private void validatePartner(Partner value) {
		value.setCode(value.getCode() == null ? null : value.getCode().trim());
		value.setName(value.getName() == null ? null : value.getName().trim());
		value.setDescription(value.getDescription() == null ? null : value.getDescription().trim());
		if (!StringUtils.hasText(value.getCode()) || value.getCode().length() > 64
				|| !value.getCode().matches("[A-Za-z0-9][A-Za-z0-9_-]{1,63}"))
			throw new IllegalArgumentException("合作方编码须为 2–64 位字母、数字、下划线或连字符");
		if (!StringUtils.hasText(value.getName()) || value.getName().length() > 128)
			throw new IllegalArgumentException("合作方名称不能为空且不能超过 128 个字符");
		if (value.getDescription() != null && value.getDescription().length() > 500)
			throw new IllegalArgumentException("合作方说明不能超过 500 个字符");
		if (value.getType() != null && !"UPSTREAM".equals(value.getType()))
			throw new IllegalArgumentException("不支持的合作方类型");
		if (value.getStatus() != null && !PARTNER_STATUSES.contains(value.getStatus()))
			throw new IllegalArgumentException("不支持的合作方状态");
	}

	private void defaults(Product value) {
		if (value.getStatus() == null)
			value.setStatus("ACTIVE");
		if (value.getVersion() == null)
			value.setVersion(0);
	}

	private void defaults(MediaChannel value) {
		if (value.getStatus() == null)
			value.setStatus("ACTIVE");
		if (value.getVersion() == null)
			value.setVersion(0);
	}

}
