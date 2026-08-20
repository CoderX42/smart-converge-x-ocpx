package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkViews;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxDeductionRule;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OcpxPromotionLinkService {

	private static final int ANDROID_IOS_PLATFORM = 3;

	private static final Set<String> GRAVITY_DYNAMIC_PARAMETERS = Set.of("idfa", "oaid", "android_id", "imei", "ip",
			"ua", "callback");

	private final OcpxPromotionLinkMapper linkMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxMonitorTaskMapper taskMapper;

	private final OcpxDeductionRuleMapper deductionRuleMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final AdvertiserScope advertiserScope;

	public IPage<PromotionLinkViews.Item> list(Page<OcpxPromotionLink> page, String keyword, String advertiserId,
			String promotionLinkId, Integer platform) {
		String scopedAdvertiserId = advertiserScope.constrain(advertiserId);
		IPage<OcpxPromotionLink> result = linkMapper.selectPage(page, Wrappers.<OcpxPromotionLink>lambdaQuery()
			.eq(StringUtils.hasText(promotionLinkId), OcpxPromotionLink::getId, promotionLinkId)
			.and(StringUtils.hasText(keyword) && !StringUtils.hasText(promotionLinkId),
					q -> q.like(OcpxPromotionLink::getId, keyword).or().like(OcpxPromotionLink::getLinkName, keyword))
			.eq(StringUtils.hasText(scopedAdvertiserId), OcpxPromotionLink::getAdvertiserId, scopedAdvertiserId)
			.eq(platform != null, OcpxPromotionLink::getPlatform, platform)
			.orderByDesc(OcpxPromotionLink::getUpdateTime));
		Map<String, OcpxAdvertiser> advertisers = advertisers(result.getRecords());
		return result.convert(link -> view(link, advertisers.get(link.getAdvertiserId())));
	}

	public List<PromotionLinkViews.AdvertiserOption> advertiserOptions(String keyword) {
		String scopedAdvertiserId = advertiserScope.currentAdvertiserId();
		return advertiserMapper.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery()
			.eq(StringUtils.hasText(scopedAdvertiserId), OcpxAdvertiser::getId, scopedAdvertiserId)
			.and(StringUtils.hasText(keyword),
					q -> q.like(OcpxAdvertiser::getId, keyword).or().like(OcpxAdvertiser::getAdvertiserName, keyword))
			.orderByAsc(OcpxAdvertiser::getAdvertiserName))
			.stream()
			.map(a -> new PromotionLinkViews.AdvertiserOption(a.getId(), a.getAdvertiserName()))
			.toList();
	}

	/** 对齐 SGYZ getLinkName：按广告主过滤推广链接下拉。 */
	public List<PromotionLinkViews.Option> options(String advertiserId) {
		String scopedAdvertiserId = advertiserScope.constrain(advertiserId);
		return linkMapper
			.selectList(Wrappers.<OcpxPromotionLink>lambdaQuery()
				.eq(StringUtils.hasText(scopedAdvertiserId), OcpxPromotionLink::getAdvertiserId, scopedAdvertiserId)
				.orderByDesc(OcpxPromotionLink::getId))
			.stream()
			.map(item -> new PromotionLinkViews.Option(item.getId(), item.getLinkName()))
			.toList();
	}

	public PromotionLinkViews.Item detail(String id) {
		OcpxPromotionLink link = require(id);
		advertiserScope.requireOwner(link.getAdvertiserId());
		return view(link, advertiserMapper.selectById(link.getAdvertiserId()));
	}

	@Transactional
	public PromotionLinkViews.Item create(PromotionLinkRequests.Create request) {
		advertiserScope.requireOwner(request.getAdvertiserId());
		OcpxAdvertiser advertiser = requireAdvertiser(request.getAdvertiserId());
		validateProtocolLink(advertiser, request.getPlatform(), request.getLinkUrl());
		OcpxPromotionLink link = new OcpxPromotionLink();
		apply(link, request.getAdvertiserId(), request.getLinkName(), request.getPlatform(), request.getLinkChannel(),
				request.getLinkUrl(), request.getDeeplink(), request.getLinkExt(), request.getRemark());
		link.setDeleted(false);
		linkMapper.insert(link);
		configRefreshPublisher.mark();
		return detail(link.getId());
	}

	@Transactional
	public PromotionLinkViews.Item update(String id, PromotionLinkRequests.Update request) {
		OcpxPromotionLink current = require(id);
		advertiserScope.requireOwner(current.getAdvertiserId());
		advertiserScope.requireOwner(request.getAdvertiserId());
		OcpxAdvertiser advertiser = requireAdvertiser(request.getAdvertiserId());
		validateProtocolLink(advertiser, request.getPlatform(), request.getLinkUrl());
		if (!current.getAdvertiserId().equals(request.getAdvertiserId()) && (taskMapper
			.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery().eq(OcpxMonitorTask::getPromotionLinkId, id)) > 0
				|| linkMapper.countCreativeBindings(id) > 0)) {
			throw new BootBusinessException("已被监测任务或创意引用的推广链接不能更换广告主");
		}
		OcpxPromotionLink update = new OcpxPromotionLink();
		update.setId(id);
		apply(update, request.getAdvertiserId(), request.getLinkName(), request.getPlatform(), request.getLinkChannel(),
				request.getLinkUrl(), request.getDeeplink(), request.getLinkExt(), request.getRemark());
		linkMapper.updateById(update);
		configRefreshPublisher.mark();
		return detail(id);
	}

	@Transactional
	public void delete(String id) {
		OcpxPromotionLink current = require(id);
		advertiserScope.requireOwner(current.getAdvertiserId());
		if (taskMapper
			.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery().eq(OcpxMonitorTask::getPromotionLinkId, id)) > 0) {
			throw new BootBusinessException("推广链接仍被监测任务引用");
		}
		if (deductionRuleMapper
			.selectCount(Wrappers.<OcpxDeductionRule>lambdaQuery().eq(OcpxDeductionRule::getPromotionLinkId, id)) > 0) {
			throw new BootBusinessException("推广链接仍被扣量规则引用");
		}
		linkMapper.deleteCreativeBindings(id);
		linkMapper.deleteById(id);
		configRefreshPublisher.mark();
	}

	/** 对齐 SGYZ addLink：投放/唤起链接按提交值落库，允许自定义 scheme。 */
	private void apply(OcpxPromotionLink link, String advertiserId, String name, Integer platform, String channel,
			String url, String deeplink, String linkExt, String remark) {
		link.setAdvertiserId(advertiserId);
		link.setLinkName(name.trim());
		link.setPlatform(platform);
		link.setLinkChannel(trim(channel));
		link.setLinkUrl(url.trim());
		link.setDeeplink(trim(deeplink));
		link.setLinkExt(trim(linkExt));
		link.setRemark(trim(remark));
	}

	private OcpxAdvertiser requireAdvertiser(String id) {
		OcpxAdvertiser advertiser = advertiserMapper.selectById(id);
		if (advertiser == null)
			throw new BootBusinessException("广告主不存在");
		return advertiser;
	}

	private void validateProtocolLink(OcpxAdvertiser advertiser, Integer platform, String url) {
		if (advertiser.getUpstreamProtocol() != com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol.GRAVITY) {
			return;
		}
		if (!Integer.valueOf(ANDROID_IOS_PLATFORM).equals(platform)) {
			throw new BootBusinessException("引力推广链接的平台必须选择 Android+IOS");
		}
		if (containsBrace(url)) {
			throw new BootBusinessException("引力投放链接必须是无动态宏的标准 HTTPS 地址");
		}
		try {
			URI uri = URI.create(url.trim());
			if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
					|| uri.getUserInfo() != null || uri.getFragment() != null || uri.getPort() != -1
					|| url.contains("\\")) {
				throw new BootBusinessException("引力投放链接必须是无动态宏的标准 HTTPS 地址");
			}
			if (StringUtils.hasText(uri.getRawQuery())) {
				for (String pair : uri.getRawQuery().split("&")) {
					String rawName = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
					String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
					if (GRAVITY_DYNAMIC_PARAMETERS.contains(name)) {
						throw new BootBusinessException("引力投放链接不能预置设备、IP、UA 或 Callback 动态参数");
					}
				}
			}
		}
		catch (BootBusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new BootBusinessException("引力投放链接格式无效");
		}
	}

	private boolean containsBrace(String value) {
		String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
		return decoded.contains("{") || decoded.contains("}");
	}

	private OcpxPromotionLink require(String id) {
		OcpxPromotionLink value = linkMapper.selectById(id);
		if (value == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "推广链接不存在");
		return value;
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private Map<String, OcpxAdvertiser> advertisers(List<OcpxPromotionLink> links) {
		List<String> ids = links.stream()
			.map(OcpxPromotionLink::getAdvertiserId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return advertiserMapper.selectBatchIds(ids)
			.stream()
			.collect(Collectors.toMap(OcpxAdvertiser::getId, Function.identity()));
	}

	private PromotionLinkViews.Item view(OcpxPromotionLink link, OcpxAdvertiser a) {
		return new PromotionLinkViews.Item(link.getId(), link.getAdvertiserId(),
				a == null ? null : a.getAdvertiserName(), link.getLinkName(), link.getPlatform(), link.getLinkChannel(),
				link.getLinkUrl(), link.getDeeplink(), link.getLinkExt(), link.getRemark(), link.getCreateTime(),
				link.getUpdateTime());
	}

}
