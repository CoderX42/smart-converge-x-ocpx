package com.yzytech.scx.commercial.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.commercial.entity.*;
import com.yzytech.scx.commercial.mapper.*;
import com.yzytech.scx.common.security.util.SecurityUtils;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Qualification case lifecycle management.
 * <p>
 * State machine: DRAFT → SUBMITTED → AUTO_CHECKING / MANUAL_REVIEW → APPROVED / REJECTED
 * / SUPPLEMENT_REQUIRED. APPEOVED cases may become SUSPENDED or EXPIRED. Every transition
 * appends an immutable review log.
 */
@Service
@RequiredArgsConstructor
public class QualificationService {

	private final QualificationCaseMapper caseMapper;

	private final QualificationDocumentMapper docMapper;

	private final QualificationReviewLogMapper reviewLogMapper;

	private final OrganizationMapper orgMapper;

	private final OrganizationScopeService scopeService;

	private final StorageUploadService storageUploadService;

	private static final long MAX_DOCUMENT_SIZE = 20L * 1024 * 1024;

	private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "doc", "docx");

	// ── Case CRUD ──

	@Transactional
	public QualificationCase createCase(String orgId, String caseType) {
		scopeService.requireWrite(orgId);
		if (orgMapper.selectById(orgId) == null)
			throw new IllegalArgumentException("资源不存在");
		QualificationCase c = new QualificationCase();
		c.setOrgId(orgId);
		c.setCaseType(caseType);
		c.setStatus("DRAFT");
		caseMapper.insert(c);
		appendLog(c.getId(), null, "DRAFT", "创建资质案件");
		return c;
	}

	public QualificationCase requireCase(String id) {
		QualificationCase c = caseMapper.selectById(id);
		if (c == null || !scopeService.canRead(c.getOrgId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		return c;
	}

	public Page<QualificationCase> listCases(Page<QualificationCase> page, String orgId, String status) {
		if (StringUtils.hasText(orgId)) {
			scopeService.requireRead(orgId);
		}
		List<String> readableIds = scopeService.readableOrganizationIds();
		if (!scopeService.isAdmin() && readableIds.isEmpty()) {
			page.setRecords(List.of());
			page.setTotal(0);
			return page;
		}
		var q = Wrappers.<QualificationCase>lambdaQuery()
			.in(!scopeService.isAdmin(), QualificationCase::getOrgId, readableIds)
			.eq(StringUtils.hasText(orgId), QualificationCase::getOrgId, orgId)
			.eq(StringUtils.hasText(status), QualificationCase::getStatus, status);
		return caseMapper.selectPage(page,
				q.orderByDesc(QualificationCase::getCreateTime).orderByDesc(QualificationCase::getId));
	}

	// ── State transitions ──

	@Transactional
	public void submitCase(String id) {
		QualificationCase c = requireCase(id);
		scopeService.requireWrite(c.getOrgId());
		if (!"DRAFT".equals(c.getStatus()) && !"SUPPLEMENT_REQUIRED".equals(c.getStatus()))
			throw new IllegalArgumentException("当前状态不可提交: " + c.getStatus());
		String from = c.getStatus();
		c.setStatus("SUBMITTED");
		c.setSubmittedAt(LocalDateTime.now());
		caseMapper.updateById(c);
		appendLog(c.getId(), from, "SUBMITTED", "提交审核");
	}

	@Transactional
	public void reviewCase(String id, String decision, String comment) {
		reviewCase(id, decision, comment, null);
	}

	@Transactional
	public void reviewCase(String id, String decision, String comment, LocalDateTime expiredAt) {
		QualificationCase c = requireCase(id);
		scopeService.requireWrite(c.getOrgId());
		if (!"SUBMITTED".equals(c.getStatus()) && !"AUTO_CHECKING".equals(c.getStatus())
				&& !"MANUAL_REVIEW".equals(c.getStatus()))
			throw new IllegalArgumentException("当前状态不可审核: " + c.getStatus());

		String from = c.getStatus();
		String to = switch (decision) {
			case "APPROVE" -> "APPROVED";
			case "REJECT" -> "REJECTED";
			case "REQUIRE_SUPPLEMENT" -> "SUPPLEMENT_REQUIRED";
			default -> throw new IllegalArgumentException("无效审核决定: " + decision);
		};
		if (("REJECTED".equals(to) || "SUPPLEMENT_REQUIRED".equals(to)) && !StringUtils.hasText(comment))
			throw new IllegalArgumentException("驳回或要求补充材料时必须填写原因");

		c.setStatus(to);
		c.setReviewedAt(LocalDateTime.now());
		c.setReviewerId(currentUserId());
		c.setReviewComment(comment);
		if ("APPROVED".equals(to)) {
			if (expiredAt != null && !expiredAt.isAfter(LocalDateTime.now())) {
				throw new IllegalArgumentException("资质失效时间必须晚于当前时间");
			}
			c.setExpiredAt(expiredAt);
		}
		caseMapper.updateById(c);
		appendLog(c.getId(), from, to, comment);
	}

	@Transactional
	public void suspendCase(String id, String reason) {
		QualificationCase c = requireCase(id);
		scopeService.requireWrite(c.getOrgId());
		if (!"APPROVED".equals(c.getStatus()))
			throw new IllegalArgumentException("只有已通过的资质才能暂停");
		if (!StringUtils.hasText(reason))
			throw new IllegalArgumentException("暂停资质必须填写原因");
		String from = c.getStatus();
		c.setStatus("SUSPENDED");
		caseMapper.updateById(c);
		appendLog(c.getId(), from, "SUSPENDED", reason);
	}

	// ── Documents ──

	public QualificationDocument addDocument(String caseId, String docType, String docName, String fileUrl) {
		QualificationCase qualificationCase = requireCase(caseId);
		scopeService.requireWrite(qualificationCase.getOrgId());
		QualificationDocument doc = new QualificationDocument();
		doc.setCaseId(caseId);
		doc.setDocType(docType);
		doc.setDocName(docName);
		doc.setFileUrl(fileUrl);
		doc.setStatus("ACTIVE");
		docMapper.insert(doc);
		return doc;
	}

	@Transactional
	public QualificationDocument uploadDocument(String caseId, String docType, MultipartFile file) {
		QualificationCase qualificationCase = requireCase(caseId);
		scopeService.requireWrite(qualificationCase.getOrgId());
		String fileName = cleanFileName(file);
		byte[] content = readAndValidate(file, fileName);
		String mimeType = contentType(fileName);
		ManagedStorageObject stored = storageUploadService.uploadManaged(new ByteArrayInputStream(content), fileName,
				mimeType, content.length);
		try {
			QualificationDocument doc = new QualificationDocument();
			doc.setCaseId(caseId);
			doc.setDocType(docType);
			doc.setDocName(fileName);
			doc.setFileUrl(stored.url());
			doc.setOriginalFileName(fileName);
			doc.setMimeType(mimeType);
			doc.setFileSize((long) content.length);
			doc.setStorageConfigId(stored.storageConfigId());
			doc.setStorageProvider(stored.provider());
			doc.setStorageObjectKey(stored.objectKey());
			doc.setContentHash(sha256(content));
			doc.setStatus("ACTIVE");
			docMapper.insert(doc);
			return doc;
		}
		catch (RuntimeException exception) {
			try {
				storageUploadService.delete(stored);
			}
			catch (RuntimeException cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
			throw exception;
		}
	}

	public QualificationDocumentContent openDocument(String documentId) {
		QualificationDocument doc = docMapper.selectById(documentId);
		if (doc == null || !"ACTIVE".equals(doc.getStatus()))
			throw new IllegalArgumentException("资源不存在");
		requireCase(doc.getCaseId());
		ManagedStorageObject object;
		if (StringUtils.hasText(doc.getStorageConfigId()) && StringUtils.hasText(doc.getStorageProvider())
				&& StringUtils.hasText(doc.getStorageObjectKey())) {
			object = new ManagedStorageObject(doc.getStorageConfigId(), doc.getStorageProvider(),
					doc.getStorageObjectKey(), doc.getFileUrl());
		}
		else {
			object = storageUploadService.resolve(doc.getFileUrl());
		}
		return new QualificationDocumentContent(doc, storageUploadService.open(object));
	}

	public java.util.List<QualificationDocument> listDocuments(String caseId) {
		requireCase(caseId);
		return docMapper.selectList(Wrappers.<QualificationDocument>lambdaQuery()
			.eq(QualificationDocument::getCaseId, caseId)
			.eq(QualificationDocument::getStatus, "ACTIVE"));
	}

	private byte[] readAndValidate(MultipartFile file, String fileName) {
		if (file == null || file.isEmpty())
			throw new IllegalArgumentException("请选择需要上传的资质文件");
		if (file.getSize() > MAX_DOCUMENT_SIZE)
			throw new IllegalArgumentException("资质文件大小不能超过 20MB");
		String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		if (!DOCUMENT_EXTENSIONS.contains(extension))
			throw new IllegalArgumentException("仅支持 PDF、图片和 Word 资质文件");
		try {
			byte[] content = file.getBytes();
			if (content.length == 0 || content.length > MAX_DOCUMENT_SIZE)
				throw new IllegalArgumentException("资质文件为空或超过 20MB");
			return content;
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("读取资质文件失败", exception);
		}
	}

	private String cleanFileName(MultipartFile file) {
		String fileName = StringUtils.cleanPath(
				file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
		if (!StringUtils.hasText(fileName) || fileName.length() > 255 || !fileName.contains(".")
				|| fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")
				|| fileName.chars().anyMatch(Character::isISOControl))
			throw new IllegalArgumentException("文件名无效");
		return fileName;
	}

	private String contentType(String fileName) {
		String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		return switch (extension) {
			case "pdf" -> "application/pdf";
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "doc" -> "application/msword";
			case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			default -> "application/octet-stream";
		};
	}

	private String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("系统不支持 SHA-256", exception);
		}
	}

	public record QualificationDocumentContent(QualificationDocument document, InputStream stream) {
	}

	// ── Review logs (immutable) ──

	public java.util.List<QualificationReviewLog> listReviewLogs(String caseId) {
		requireCase(caseId);
		return reviewLogMapper.selectList(Wrappers.<QualificationReviewLog>lambdaQuery()
			.eq(QualificationReviewLog::getCaseId, caseId)
			.orderByDesc(QualificationReviewLog::getCreatedAt));
	}

	private void appendLog(String caseId, String fromStatus, String toStatus, String comment) {
		QualificationReviewLog log = new QualificationReviewLog();
		log.setCaseId(caseId);
		log.setFromStatus(fromStatus);
		log.setToStatus(toStatus);
		log.setOperatorId(currentUserId());
		log.setOperatorName(currentUserName());
		log.setComment(comment);
		reviewLogMapper.insert(log);
	}

	private String currentUserId() {
		try {
			var user = SecurityUtils.getUser();
			return user != null ? user.getUserId() : "system";
		}
		catch (Exception e) {
			return "system";
		}
	}

	private String currentUserName() {
		try {
			var user = SecurityUtils.getUser();
			return user != null ? user.getUsername() : "system";
		}
		catch (Exception e) {
			return "system";
		}
	}

}
