package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.MediaRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaViews;
import com.yzytech.scx.ocpx.admin.util.OcpxCsv;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxMediaService {

	private static final Set<String> ADAPTERS = Set.of("STANDARD", "TEMPLATE_GET_DOWNSTREAM", "MI");

	private final OcpxMediaMapper mediaMapper;

	private final OcpxMonitorTaskMapper taskMapper;

	private final OcpxUrlPolicy urlPolicy;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	public IPage<MediaViews.Item> list(Page<OcpxMedia> page, String keyword, String adapterCode, String status) {
		return mediaMapper
			.selectPage(page, Wrappers.<OcpxMedia>lambdaQuery()
				.and(StringUtils.hasText(keyword),
						q -> q.like(OcpxMedia::getMediaCode, keyword).or().like(OcpxMedia::getMediaName, keyword))
				.eq(StringUtils.hasText(adapterCode), OcpxMedia::getAdapterCode, adapterCode)
				.eq(StringUtils.hasText(status), OcpxMedia::getStatus, status)
				.orderByDesc(OcpxMedia::getUpdateTime))
			.convert(this::view);
	}

	public String exportCsv(String keyword, String adapterCode, String status) {
		List<OcpxMedia> data = mediaMapper.selectList(Wrappers.<OcpxMedia>lambdaQuery()
			.and(StringUtils.hasText(keyword),
					q -> q.like(OcpxMedia::getMediaCode, keyword).or().like(OcpxMedia::getMediaName, keyword))
			.eq(StringUtils.hasText(adapterCode), OcpxMedia::getAdapterCode, adapterCode)
			.eq(StringUtils.hasText(status), OcpxMedia::getStatus, status)
			.orderByDesc(OcpxMedia::getUpdateTime));
		if (data.isEmpty()) {
			return "";
		}
		return OcpxCsv.content(List.of("媒体ID", "媒体名称", "'uri'", "'宏参数'", "创建时间", "修改时间"),
				data.stream()
					.map(media -> Arrays.<Object>asList(media.getId(), media.getMediaName(), media.getIngressUri(),
							media.getMacroParams(), csvTime(media.getCreateTime()), csvTime(media.getUpdateTime())))
					.toList());
	}

	private String csvTime(LocalDateTime value) {
		return value == null ? null : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	public MediaViews.Item detail(String id) {
		return view(require(id));
	}

	@Transactional
	public MediaViews.Item create(MediaRequests.Create request) {
		ensureCode(null, request.mediaCode());
		validateCredential(request.authType(), request.credentialConfigKey(), request.credentialVersion());
		OcpxMedia media = new OcpxMedia();
		media.setMediaCode(request.mediaCode().trim());
		apply(media, request.mediaName(), request.adapterCode(), request.authType(), request.credentialConfigKey(),
				request.credentialVersion(), request.ingressUri(), request.macroParams(), request.callbackUrl(),
				request.callbackHostAllowlist());
		media.setStatus("ACTIVE");
		media.setConfigVersion(1L);
		media.setDeleted(false);
		mediaMapper.insert(media);
		configRefreshPublisher.mark();
		return detail(media.getId());
	}

	@Transactional
	public MediaViews.Item update(String id, MediaRequests.Update request) {
		OcpxMedia current = require(id);
		requireVersion(current, request.expectedVersion());
		validateCredential(request.authType(), request.credentialConfigKey(), request.credentialVersion());
		OcpxMedia update = new OcpxMedia();
		update.setId(id);
		apply(update, request.mediaName(), request.adapterCode(), request.authType(), request.credentialConfigKey(),
				request.credentialVersion(), request.ingressUri(), request.macroParams(), request.callbackUrl(),
				request.callbackHostAllowlist());
		update.setConfigVersion(current.getConfigVersion() + 1);
		if (mediaMapper.update(update,
				Wrappers.<OcpxMedia>lambdaUpdate()
					.eq(OcpxMedia::getId, id)
					.eq(OcpxMedia::getConfigVersion, request.expectedVersion())) != 1)
			throw conflict();
		configRefreshPublisher.mark();
		return detail(id);
	}

	@Transactional
	public MediaViews.Mutation changeStatus(String id, MediaRequests.StatusAction request) {
		OcpxMedia current = require(id);
		requireVersion(current, request.expectedVersion());
		long next = current.getConfigVersion() + 1;
		if (mediaMapper.update(null,
				Wrappers.<OcpxMedia>lambdaUpdate()
					.eq(OcpxMedia::getId, id)
					.eq(OcpxMedia::getConfigVersion, request.expectedVersion())
					.set(OcpxMedia::getStatus, request.status())
					.set(OcpxMedia::getConfigVersion, next)) != 1)
			throw conflict();
		configRefreshPublisher.mark();
		return new MediaViews.Mutation(id, request.status(), next);
	}

	@Transactional
	public MediaViews.Mutation archive(String id, MediaRequests.VersionedAction request) {
		OcpxMedia current = require(id);
		requireVersion(current, request.expectedVersion());
		if (taskMapper.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery().eq(OcpxMonitorTask::getMediaId, id)) > 0)
			throw new IllegalArgumentException("媒体仍被监测任务引用");
		mediaMapper.deleteById(id);
		configRefreshPublisher.mark();
		return new MediaViews.Mutation(id, "ARCHIVED", current.getConfigVersion() + 1);
	}

	public MediaViews.Preflight preflight(String id) {
		OcpxMedia media = require(id);
		boolean credential = "NONE".equals(media.getAuthType()) || StringUtils.hasText(media.getCredentialConfigKey());
		boolean callback = StringUtils.hasText(media.getCallbackUrl());
		List<String> issues = new java.util.ArrayList<>();
		if (!ADAPTERS.contains(media.getAdapterCode()))
			issues.add("ADAPTER_NOT_SUPPORTED");
		if (!credential)
			issues.add("CREDENTIAL_REFERENCE_INCOMPLETE");
		if (!callback)
			issues.add("CALLBACK_URL_MISSING");
		return new MediaViews.Preflight(issues.isEmpty() ? "PASS" : "BLOCK", ADAPTERS.contains(media.getAdapterCode()),
				callback, credential, List.copyOf(issues));
	}

	public MediaViews.Connectivity connectivity(String id) {
		preflight(id);
		return new MediaViews.Connectivity("NOT_EXECUTED", false, "真实回调在 Callback 流程中同步执行");
	}

	private void apply(OcpxMedia media, String name, String adapter, String authType, String key, Long version,
			String ingressUri, String macros, String callbackUrl, String allowlist) {
		String normalized = adapter.trim().toUpperCase();
		if (!ADAPTERS.contains(normalized))
			throw new IllegalArgumentException("下游 Adapter 不受支持");
		if (StringUtils.hasText(callbackUrl)) {
			OcpxUrlPolicy.Result validation = urlPolicy.validate(callbackUrl);
			if (!"PASS".equals(validation.status()))
				throw new IllegalArgumentException("媒体 callback URL 校验失败");
			if (StringUtils.hasText(allowlist) && java.util.Arrays.stream(allowlist.split(","))
				.noneMatch(host -> host.trim().equalsIgnoreCase(validation.host())))
				throw new IllegalArgumentException("媒体 callback host 不在白名单中");
			callbackUrl = validation.normalizedUrl();
		}
		media.setMediaName(name.trim());
		media.setAdapterCode(normalized);
		media.setAuthType(authType);
		media.setCredentialConfigKey(trim(key));
		media.setCredentialVersion(version);
		media.setIngressUri(ingressUri.trim());
		media.setMacroParams(trim(macros));
		media.setCallbackUrl(trim(callbackUrl));
		media.setCallbackHostAllowlist(trim(allowlist));
	}

	private void validateCredential(String auth, String key, Long version) {
		if (!"NONE".equals(auth) && (!StringUtils.hasText(key) || version == null))
			throw new IllegalArgumentException("媒体鉴权需要凭据配置键与版本");
		if ("NONE".equals(auth) && (StringUtils.hasText(key) || version != null))
			throw new IllegalArgumentException("NONE 鉴权不能配置凭据引用");
	}

	private void ensureCode(String id, String code) {
		if (mediaMapper.selectCount(Wrappers.<OcpxMedia>lambdaQuery()
			.eq(OcpxMedia::getMediaCode, code.trim())
			.ne(StringUtils.hasText(id), OcpxMedia::getId, id)) > 0)
			throw new IllegalArgumentException("媒体编码已存在");
	}

	private OcpxMedia require(String id) {
		OcpxMedia value = mediaMapper.selectById(id);
		if (value == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "媒体不存在");
		return value;
	}

	private void requireVersion(OcpxMedia value, Long expected) {
		if (!expected.equals(value.getConfigVersion()))
			throw conflict();
	}

	private BootBusinessException conflict() {
		return new BootBusinessException(409, "配置已被修改，请刷新后重试");
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private List<String> split(String value) {
		return StringUtils.hasText(value)
				? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::hasText).toList()
				: List.of();
	}

	private MediaViews.Item view(OcpxMedia m) {
		return new MediaViews.Item(m.getId(), m.getMediaCode(), m.getMediaName(), m.getAdapterCode(), m.getAuthType(),
				m.getCredentialConfigKey(), m.getCredentialVersion(), m.getIngressUri(), split(m.getMacroParams()),
				m.getCallbackUrl(), split(m.getCallbackHostAllowlist()), m.getStatus(), m.getConfigVersion(),
				m.getCreateTime(), m.getUpdateTime());
	}

}
