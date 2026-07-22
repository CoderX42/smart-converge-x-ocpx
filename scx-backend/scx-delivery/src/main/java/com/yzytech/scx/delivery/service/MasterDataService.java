package com.yzytech.scx.delivery.service;

import com.yzytech.scx.delivery.entity.*;
import com.yzytech.scx.delivery.mapper.*;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.system.api.service.StorageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MasterDataService {
    private static final Set<String> SANDBOX_HOSTS = Set.of("localhost", "127.0.0.1", "callback-mock", "minio");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "csv", "doc", "docx", "json", "md", "pdf", "txt", "xlsx", "yaml", "yml");
    private static final long MAX_DOCUMENT_SIZE = 20L * 1024 * 1024;
    private final MediaScopeService scopeService;
    private final PartnerMapper partnerMapper;
    private final UpstreamCustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final IntegrationDocumentMapper documentMapper;
    private final MediaChannelMapper channelMapper;
    private final StorageUploadService storageUploadService;

    public boolean readable(String mediaId) { return scopeService.canRead(mediaId); }
    public boolean writable(String mediaId) { return scopeService.canWrite(mediaId); }

    @Transactional
    public boolean savePartner(Partner value) {
        requireWrite(value.getMediaId());
        if (value.getId() != null) {
            Partner existing = partnerMapper.selectById(value.getId());
            requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
        }
        defaults(value);
        return value.getId() == null ? partnerMapper.insert(value) == 1 : partnerMapper.updateById(value) == 1;
    }
    @Transactional
    public boolean saveCustomer(UpstreamCustomer value) {
        requireWrite(value.getMediaId());
        if (value.getId() != null) {
            UpstreamCustomer existing = customerMapper.selectById(value.getId());
            requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
        }
        if (value.getStatus() == null) value.setStatus("ACTIVE");
        if (value.getVersion() == null) value.setVersion(0);
        return value.getId() == null ? customerMapper.insert(value) == 1 : customerMapper.updateById(value) == 1;
    }
    @Transactional
    public boolean saveProduct(Product value) {
        requireWrite(value.getMediaId());
        if (value.getId() != null) {
            Product existing = productMapper.selectById(value.getId());
            requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
        }
        Partner partner = partnerMapper.selectById(value.getPartnerId());
        if (partner == null || !value.getMediaId().equals(partner.getMediaId())) throw new IllegalArgumentException("合作方不在当前媒体范围");
        if (StringUtils.hasText(value.getCustomerId())) {
            UpstreamCustomer customer = customerMapper.selectById(value.getCustomerId());
            if (customer == null || !value.getMediaId().equals(customer.getMediaId())) throw new IllegalArgumentException("上游客户不在当前媒体范围");
        }
        defaults(value);
        return value.getId() == null ? productMapper.insert(value) == 1 : productMapper.updateById(value) == 1;
    }
    @Transactional
    public boolean saveDocument(IntegrationDocument value) {
        requireWrite(value.getMediaId());
        if (value.getId() != null) {
            IntegrationDocument existing = documentMapper.selectById(value.getId());
            requireSameMedia(existing == null ? null : existing.getMediaId(), value.getMediaId());
        }
        if (value.getType() != null && !IntegrationDocumentType.isValid(value.getType()))
            throw new IllegalArgumentException("不支持的文档类型: " + value.getType());
        if (StringUtils.hasText(value.getObjectKey()) == StringUtils.hasText(value.getExternalUrl())) throw new IllegalArgumentException("对象键与外部 URL 必须二选一");
        if (StringUtils.hasText(value.getExternalUrl())) validateSandboxUrl(value.getExternalUrl());
        if (value.getStatus() == null) value.setStatus("DRAFT");
        if (value.getDocumentVersion() == null) value.setDocumentVersion(1);
        if (value.getVersion() == null) value.setVersion(0);
        return value.getId() == null ? documentMapper.insert(value) == 1 : documentMapper.updateById(value) == 1;
    }
    public String uploadDocument(String mediaId, MultipartFile file) {
        requireWrite(mediaId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择需要上传的文档");
        if (file.getSize() > MAX_DOCUMENT_SIZE) throw new IllegalArgumentException("文档大小不能超过 20MB");
        String fileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (!StringUtils.hasText(fileName) || fileName.contains("..")) throw new IllegalArgumentException("文件名无效");
        int extensionIndex = fileName.lastIndexOf('.');
        String extension = extensionIndex < 1 ? "" : fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (!DOCUMENT_EXTENSIONS.contains(extension)) throw new IllegalArgumentException("仅支持 PDF、Office、文本、CSV、JSON 和 YAML 文档");
        try (InputStream inputStream = file.getInputStream()) {
            return storageUploadService.upload(inputStream, fileName, file.getContentType(), file.getSize());
        }
        catch (IOException e) {
            throw new IllegalArgumentException("读取上传文档失败");
        }
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
            if (document == null || !value.getMediaId().equals(document.getMediaId())) throw new IllegalArgumentException("接入文档不在当前媒体范围");
        }
        defaults(value);
        return value.getId() == null ? channelMapper.insert(value) == 1 : channelMapper.updateById(value) == 1;
    }
    public void validateSandboxUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null || !SANDBOX_HOSTS.contains(uri.getHost())) {
                throw new IllegalArgumentException("仅允许沙箱 allowlist URL");
            }
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("URL 格式无效"); }
    }
    private void requireWrite(String mediaId) { if (!scopeService.canWrite(mediaId)) throw new IllegalArgumentException("资源不存在"); }
    private void requireSameMedia(String existingMediaId, String requestedMediaId) {
        if (existingMediaId == null || !existingMediaId.equals(requestedMediaId) || !scopeService.canWrite(existingMediaId)) {
            throw new IllegalArgumentException("资源不存在");
        }
    }
    private void defaults(Partner value) { if (value.getStatus() == null) value.setStatus("ACTIVE"); if (value.getType() == null) value.setType("UPSTREAM"); if (value.getVersion() == null) value.setVersion(0); }
    private void defaults(Product value) { if (value.getStatus() == null) value.setStatus("ACTIVE"); if (value.getVersion() == null) value.setVersion(0); }
    private void defaults(MediaChannel value) { if (value.getStatus() == null) value.setStatus("ACTIVE"); if (value.getVersion() == null) value.setVersion(0); }
}
