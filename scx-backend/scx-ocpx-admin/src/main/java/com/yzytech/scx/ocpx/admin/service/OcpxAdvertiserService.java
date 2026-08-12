package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxAdvertiserService {

	private static final Set<String> ADAPTERS = Set.of("COCO", "TEMPLATE_GET");

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxPromotionLinkMapper promotionLinkMapper;

	private final OcpxUrlPolicy urlPolicy;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	public IPage<AdvertiserViews.Item> list(Page<OcpxAdvertiser> page, String keyword, String adapterCode,
			String status) {
		return advertiserMapper
			.selectPage(page,
					Wrappers.<OcpxAdvertiser>lambdaQuery()
						.and(StringUtils.hasText(keyword),
								q -> q.like(OcpxAdvertiser::getAdvertiserCode, keyword)
									.or()
									.like(OcpxAdvertiser::getAdvertiserName, keyword))
						.eq(StringUtils.hasText(adapterCode), OcpxAdvertiser::getAdapterCode, adapterCode)
						.eq(StringUtils.hasText(status), OcpxAdvertiser::getStatus, status)
						.orderByDesc(OcpxAdvertiser::getUpdateTime))
			.convert(this::view);
	}

	public AdvertiserViews.Item detail(String id) {
		return view(require(id));
	}

	@Transactional
	public AdvertiserViews.Item create(AdvertiserRequests.Create request) {
		ensureUnique(null, request.code(), request.name());
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setAdvertiserCode(request.code().trim());
		apply(advertiser, request.name(), request.adapterCode(), request.credentialConfigKey(),
				request.credentialVersion(), request.bidUrl(), request.macroParams(), request.upstreamHostAllowlist());
		advertiser.setStatus("ACTIVE");
		advertiser.setConfigVersion(1L);
		advertiser.setDeleted(false);
		advertiserMapper.insert(advertiser);
		configRefreshPublisher.mark();
		return detail(advertiser.getId());
	}

	@Transactional
	public AdvertiserViews.Item update(String id, AdvertiserRequests.Update request) {
		OcpxAdvertiser current = require(id);
		requireVersion(current, request.expectedVersion());
		ensureUnique(id, current.getAdvertiserCode(), request.name());
		OcpxAdvertiser update = new OcpxAdvertiser();
		update.setId(id);
		apply(update, request.name(), request.adapterCode(), request.credentialConfigKey(), request.credentialVersion(),
				request.bidUrl(), request.macroParams(), request.upstreamHostAllowlist());
		update.setConfigVersion(current.getConfigVersion() + 1);
		if (advertiserMapper.update(update,
				Wrappers.<OcpxAdvertiser>lambdaUpdate()
					.eq(OcpxAdvertiser::getId, id)
					.eq(OcpxAdvertiser::getConfigVersion, request.expectedVersion())) != 1) {
			throw conflict();
		}
		configRefreshPublisher.mark();
		return detail(id);
	}

	@Transactional
	public AdvertiserViews.Mutation changeStatus(String id, AdvertiserRequests.ChangeStatus request) {
		OcpxAdvertiser current = require(id);
		requireVersion(current, request.expectedVersion());
		long next = current.getConfigVersion() + 1;
		if (advertiserMapper.update(null,
				Wrappers.<OcpxAdvertiser>lambdaUpdate()
					.eq(OcpxAdvertiser::getId, id)
					.eq(OcpxAdvertiser::getConfigVersion, request.expectedVersion())
					.set(OcpxAdvertiser::getStatus, request.status())
					.set(OcpxAdvertiser::getConfigVersion, next)) != 1) {
			throw conflict();
		}
		configRefreshPublisher.mark();
		return new AdvertiserViews.Mutation(id, request.status(), next);
	}

	@Transactional
	public AdvertiserViews.Mutation archive(String id, AdvertiserRequests.VersionedAction request) {
		OcpxAdvertiser current = require(id);
		requireVersion(current, request.expectedVersion());
		if (promotionLinkMapper
			.selectCount(Wrappers.<OcpxPromotionLink>lambdaQuery().eq(OcpxPromotionLink::getAdvertiserId, id)) > 0) {
			throw new IllegalArgumentException("广告主仍有未归档推广链接");
		}
		advertiserMapper.deleteById(id);
		configRefreshPublisher.mark();
		return new AdvertiserViews.Mutation(id, "ARCHIVED", current.getConfigVersion() + 1);
	}

	private void apply(OcpxAdvertiser target, String name, String adapter, String key, Long version, String bidUrl,
			String macros, String allowlist) {
		String normalizedAdapter = adapter.trim().toUpperCase();
		if (!ADAPTERS.contains(normalizedAdapter)) {
			throw new IllegalArgumentException("上游 Adapter 不受支持");
		}
		OcpxUrlPolicy.Result validation = urlPolicy.validate(bidUrl);
		if (!"PASS".equals(validation.status())) {
			throw new IllegalArgumentException("上游 URL 校验失败: " + String.join(",", validation.issues()));
		}
		if (StringUtils.hasText(allowlist) && java.util.Arrays.stream(allowlist.split(","))
			.noneMatch(host -> host.trim().equalsIgnoreCase(validation.host()))) {
			throw new IllegalArgumentException("上游 host 不在白名单中");
		}
		target.setAdvertiserName(name.trim());
		target.setAdapterCode(normalizedAdapter);
		target.setCredentialConfigKey(key.trim());
		target.setCredentialVersion(version);
		target.setBidUrl(validation.normalizedUrl());
		target.setMacroParams(trim(macros));
		target.setUpstreamHostAllowlist(trim(allowlist));
	}

	private void ensureUnique(String id, String code, String name) {
		if (advertiserMapper.selectCount(Wrappers.<OcpxAdvertiser>lambdaQuery()
			.and(q -> q.eq(OcpxAdvertiser::getAdvertiserCode, code.trim())
				.or()
				.eq(OcpxAdvertiser::getAdvertiserName, name.trim()))
			.ne(StringUtils.hasText(id), OcpxAdvertiser::getId, id)) > 0) {
			throw new IllegalArgumentException("广告主编码或名称已存在");
		}
	}

	private OcpxAdvertiser require(String id) {
		OcpxAdvertiser value = advertiserMapper.selectById(id);
		if (value == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "广告主不存在");
		return value;
	}

	private void requireVersion(OcpxAdvertiser value, Long expected) {
		if (!expected.equals(value.getConfigVersion()))
			throw conflict();
	}

	private BootBusinessException conflict() {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), "配置已被其他操作修改，请刷新后重试");
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private AdvertiserViews.Item view(OcpxAdvertiser value) {
		return new AdvertiserViews.Item(value.getId(), value.getAdvertiserName(), value.getAdvertiserCode(),
				value.getAdapterCode(), value.getCredentialConfigKey(), value.getCredentialVersion(), value.getBidUrl(),
				value.getMacroParams(), value.getUpstreamHostAllowlist(), value.getStatus(), value.getConfigVersion(),
				value.getCreateTime(), value.getUpdateTime());
	}

}
