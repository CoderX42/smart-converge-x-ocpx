package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.admin.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OcpxPromotionLinkService {

	private final OcpxPromotionLinkMapper linkMapper;
	private final OcpxAdvertiserMapper advertiserMapper;
	private final OcpxMonitorTaskMapper taskMapper;
	private final OcpxUrlPolicy urlPolicy;

	public IPage<PromotionLinkViews.Item> list(Page<OcpxPromotionLink> page, String keyword, String advertiserId,
			String platform, String status) {
		return linkMapper.selectPage(page, Wrappers.<OcpxPromotionLink>lambdaQuery()
			.and(StringUtils.hasText(keyword), q -> q.like(OcpxPromotionLink::getLinkCode, keyword).or()
				.like(OcpxPromotionLink::getLinkName, keyword))
			.eq(StringUtils.hasText(advertiserId), OcpxPromotionLink::getAdvertiserId, advertiserId)
			.eq(StringUtils.hasText(platform), OcpxPromotionLink::getPlatform, platform)
			.eq(StringUtils.hasText(status), OcpxPromotionLink::getStatus, status)
			.orderByDesc(OcpxPromotionLink::getUpdateTime)).convert(this::view);
	}

	public List<PromotionLinkViews.AdvertiserOption> advertiserOptions(String keyword) {
		return advertiserMapper.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery().eq(OcpxAdvertiser::getStatus, "ACTIVE")
			.and(StringUtils.hasText(keyword), q -> q.like(OcpxAdvertiser::getAdvertiserCode, keyword).or()
				.like(OcpxAdvertiser::getAdvertiserName, keyword)).orderByAsc(OcpxAdvertiser::getAdvertiserName))
			.stream().map(a -> new PromotionLinkViews.AdvertiserOption(a.getId(), a.getAdvertiserCode(),
					a.getAdvertiserName())).toList();
	}

	public PromotionLinkViews.Item detail(String id) { return view(require(id)); }

	@Transactional
	public PromotionLinkViews.Item create(PromotionLinkRequests.Create request) {
		requireActiveAdvertiser(request.advertiserId());
		ensureCode(null, request.linkCode());
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setLinkCode(request.linkCode().trim());
		apply(link, request.advertiserId(), request.linkName(), request.packageName(), request.platform(),
				request.linkType(), request.linkChannel(), request.linkUrlTemplate(), request.deeplinkUrl(),
				request.downloadUrl(), request.redirectEnabled(), request.macroSet(), request.remark());
		link.setStatus("ACTIVE");
		link.setConfigVersion(1L);
		link.setDeleted(false);
		linkMapper.insert(link);
		return detail(link.getId());
	}

	@Transactional
	public PromotionLinkViews.Item update(String id, PromotionLinkRequests.Update request) {
		OcpxPromotionLink current = require(id);
		requireVersion(current, request.expectedVersion());
		requireActiveAdvertiser(request.advertiserId());
		if (!current.getAdvertiserId().equals(request.advertiserId())
				&& taskMapper.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery()
					.eq(OcpxMonitorTask::getPromotionLinkId, id)) > 0) {
			throw new IllegalArgumentException("已被监测任务引用的推广链接不能更换广告主");
		}
		OcpxPromotionLink update = new OcpxPromotionLink();
		update.setId(id);
		apply(update, request.advertiserId(), request.linkName(), request.packageName(), request.platform(),
				request.linkType(), request.linkChannel(), request.linkUrlTemplate(), request.deeplinkUrl(),
				request.downloadUrl(), request.redirectEnabled(), request.macroSet(), request.remark());
		update.setConfigVersion(current.getConfigVersion() + 1);
		if (linkMapper.update(update, Wrappers.<OcpxPromotionLink>lambdaUpdate().eq(OcpxPromotionLink::getId, id)
			.eq(OcpxPromotionLink::getConfigVersion, request.expectedVersion())) != 1) throw conflict();
		return detail(id);
	}

	@Transactional
	public PromotionLinkViews.Mutation changeStatus(String id, PromotionLinkRequests.StatusAction request) {
		OcpxPromotionLink current = require(id);
		requireVersion(current, request.expectedVersion());
		long next = current.getConfigVersion() + 1;
		if (linkMapper.update(null, Wrappers.<OcpxPromotionLink>lambdaUpdate().eq(OcpxPromotionLink::getId, id)
			.eq(OcpxPromotionLink::getConfigVersion, request.expectedVersion())
			.set(OcpxPromotionLink::getStatus, request.status()).set(OcpxPromotionLink::getConfigVersion, next)) != 1)
			throw conflict();
		return new PromotionLinkViews.Mutation(id, request.status(), next);
	}

	@Transactional
	public PromotionLinkViews.Mutation archive(String id, PromotionLinkRequests.VersionedAction request) {
		OcpxPromotionLink current = require(id);
		requireVersion(current, request.expectedVersion());
		if (taskMapper.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery()
			.eq(OcpxMonitorTask::getPromotionLinkId, id)) > 0) {
			throw new IllegalArgumentException("推广链接仍被监测任务引用");
		}
		linkMapper.deleteById(id);
		return new PromotionLinkViews.Mutation(id, "ARCHIVED", current.getConfigVersion() + 1);
	}

	public PromotionLinkViews.ValidationReport validateUrl(PromotionLinkRequests.ValidateUrl request) {
		OcpxUrlPolicy.Result result = urlPolicy.validate(request.url());
		return new PromotionLinkViews.ValidationReport(result.status(), false, result.normalizedMaskedUrl(),
				result.host(), result.fingerprint(), result.macroSet(), result.issues());
	}

	private void apply(OcpxPromotionLink link, String advertiserId, String name, String packageName, String platform,
			String type, String channel, String url, String deeplink, String download, Boolean redirect, String macros,
			String remark) {
		OcpxUrlPolicy.Result result = urlPolicy.validate(url);
		if (!"PASS".equals(result.status())) throw new IllegalArgumentException("推广 URL 校验失败");
		link.setAdvertiserId(advertiserId);
		link.setLinkName(name.trim()); link.setPackageName(trim(packageName)); link.setPlatform(platform);
		link.setLinkType(type); link.setLinkChannel(trim(channel)); link.setLinkUrlTemplate(result.normalizedUrl());
		link.setDeeplinkUrl(trim(deeplink)); link.setDownloadUrl(trim(download)); link.setRedirectEnabled(redirect);
		link.setMacroSet(trim(macros)); link.setRemark(trim(remark));
	}

	private void requireActiveAdvertiser(String id) {
		if (advertiserMapper.selectCount(Wrappers.<OcpxAdvertiser>lambdaQuery().eq(OcpxAdvertiser::getId, id)
			.eq(OcpxAdvertiser::getStatus, "ACTIVE")) != 1) throw new IllegalArgumentException("广告主不存在或未启用");
	}
	private void ensureCode(String id, String code) {
		if (linkMapper.selectCount(Wrappers.<OcpxPromotionLink>lambdaQuery().eq(OcpxPromotionLink::getLinkCode, code.trim())
			.ne(StringUtils.hasText(id), OcpxPromotionLink::getId, id)) > 0) throw new IllegalArgumentException("推广链接编码已存在");
	}
	private OcpxPromotionLink require(String id) {
		OcpxPromotionLink value = linkMapper.selectById(id);
		if (value == null) throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "推广链接不存在");
		return value;
	}
	private void requireVersion(OcpxPromotionLink value, Long expected) { if (!expected.equals(value.getConfigVersion())) throw conflict(); }
	private BootBusinessException conflict() { return new BootBusinessException(409, "配置已被修改，请刷新后重试"); }
	private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
	private PromotionLinkViews.Item view(OcpxPromotionLink link) {
		OcpxAdvertiser a = advertiserMapper.selectById(link.getAdvertiserId());
		return new PromotionLinkViews.Item(link.getId(), link.getAdvertiserId(), a == null ? null : a.getAdvertiserName(),
				link.getLinkCode(), link.getLinkName(), link.getPackageName(), link.getPlatform(), link.getLinkType(),
				link.getLinkChannel(), link.getLinkUrlTemplate(), link.getDeeplinkUrl(), link.getDownloadUrl(),
				split(link.getMacroSet()), Boolean.TRUE.equals(link.getRedirectEnabled()), link.getRemark(), link.getStatus(),
				link.getConfigVersion(), link.getCreateTime(), link.getUpdateTime());
	}
	private List<String> split(String value) { return StringUtils.hasText(value) ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::hasText).toList() : List.of(); }
}
