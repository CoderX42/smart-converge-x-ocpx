package com.yzytech.scx.delivery.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.mapper.RtaEndpointMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RtaEndpointService {
    private static final Set<String> STATES = Set.of("DRAFT", "ACTIVE", "DISABLED");

    private final RtaEndpointMapper mapper;
    private final MediaScopeService scopeService;
    private final MasterDataService masterDataService;

    public IPage<RtaEndpoint> page(Page<RtaEndpoint> page, String mediaId, String status, String keyword) {
        requireRead(mediaId);
        return mapper.selectPage(page, Wrappers.<RtaEndpoint>lambdaQuery()
                .eq(RtaEndpoint::getMediaId, mediaId)
                .eq(status != null && !status.isBlank(), RtaEndpoint::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), query -> query.like(RtaEndpoint::getName, keyword)
                        .or().like(RtaEndpoint::getCode, keyword))
                .orderByDesc(RtaEndpoint::getUpdateTime));
    }

    @Transactional
    public boolean save(RtaEndpoint value) {
        requireWrite(value.getMediaId());
        if (value.getId() != null) {
            RtaEndpoint existing = require(value.getId(), true);
            if (!existing.getMediaId().equals(value.getMediaId())) throw new IllegalArgumentException("资源不存在");
            if (!"DRAFT".equals(existing.getStatus())) throw new IllegalArgumentException("已启用 RTA 配置不可覆盖，请先停用后创建新配置");
        }
        masterDataService.validateSandboxUrl(value.getEndpointUrl());
        if (value.getStatus() == null) value.setStatus("DRAFT");
        value.setStatus(value.getStatus().toUpperCase());
        if (!STATES.contains(value.getStatus())) throw new IllegalArgumentException("非法 RTA 状态");
        if (value.getVersion() == null) value.setVersion(0);
        return value.getId() == null ? mapper.insert(value) == 1 : mapper.updateById(value) == 1;
    }

    @Transactional
    public boolean changeStatus(String id, String status, Integer expectedVersion) {
        RtaEndpoint endpoint = require(id, true);
        String target = status.toUpperCase();
        if (!STATES.contains(target)) throw new IllegalArgumentException("非法 RTA 状态");
        if (expectedVersion != null && !expectedVersion.equals(endpoint.getVersion())) throw new IllegalArgumentException("RTA 配置版本已变更");
        endpoint.setStatus(target);
        return mapper.updateById(endpoint) == 1;
    }

    @Transactional
    public RtaEndpoint healthCheck(String id) {
        RtaEndpoint endpoint = require(id, true);
        if (!"ACTIVE".equals(endpoint.getStatus())) throw new IllegalArgumentException("仅已启用 RTA 配置可检测");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.getEndpointUrl()))
                    .GET().timeout(Duration.ofSeconds(3)).build();
            HttpResponse<Void> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            endpoint.setLastHealthStatus(response.statusCode() >= 200 && response.statusCode() < 400 ? "AVAILABLE" : "UNAVAILABLE");
            endpoint.setLastHealthMessage("HTTP " + response.statusCode());
        }
        catch (Exception exception) {
            endpoint.setLastHealthStatus("UNAVAILABLE");
            endpoint.setLastHealthMessage("sandbox endpoint unavailable");
        }
        endpoint.setLastHealthCheckedAt(LocalDateTime.now());
        mapper.updateById(endpoint);
        return endpoint;
    }

    public RtaEndpoint require(String id, boolean write) {
        RtaEndpoint endpoint = mapper.selectById(id);
        if (endpoint == null || (write ? !scopeService.canWrite(endpoint.getMediaId()) : !scopeService.canRead(endpoint.getMediaId()))) {
            throw new IllegalArgumentException("资源不存在");
        }
        return endpoint;
    }

    private void requireRead(String mediaId) {
        if (mediaId == null || !scopeService.canRead(mediaId)) throw new IllegalArgumentException("资源不存在");
    }

    private void requireWrite(String mediaId) {
        if (mediaId == null || !scopeService.canWrite(mediaId)) throw new IllegalArgumentException("资源不存在");
    }
}
