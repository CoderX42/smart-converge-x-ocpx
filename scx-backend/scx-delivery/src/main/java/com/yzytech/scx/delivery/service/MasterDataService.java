package com.yzytech.scx.delivery.service;

import com.yzytech.scx.delivery.entity.*;
import com.yzytech.scx.delivery.mapper.*;
import com.yzytech.scx.subject.service.MediaScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MasterDataService {
    private static final Set<String> SANDBOX_HOSTS = Set.of("localhost", "127.0.0.1", "callback-mock", "minio");
    private final MediaScopeService scopeService;
    private final PartnerMapper partnerMapper;
    private final UpstreamCustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final IntegrationDocumentMapper documentMapper;
    private final MediaChannelMapper channelMapper;

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
        if (StringUtils.hasText(value.getObjectKey()) == StringUtils.hasText(value.getExternalUrl())) throw new IllegalArgumentException("对象键与外部 URL 必须二选一");
        if (StringUtils.hasText(value.getExternalUrl())) validateSandboxUrl(value.getExternalUrl());
        if (value.getStatus() == null) value.setStatus("DRAFT");
        if (value.getDocumentVersion() == null) value.setDocumentVersion(1);
        if (value.getVersion() == null) value.setVersion(0);
        return value.getId() == null ? documentMapper.insert(value) == 1 : documentMapper.updateById(value) == 1;
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
