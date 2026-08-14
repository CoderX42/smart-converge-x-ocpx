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

import java.util.List;

@Service
@RequiredArgsConstructor
public class OcpxPromotionLinkService {

	private final OcpxPromotionLinkMapper linkMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxMonitorTaskMapper taskMapper;

	private final OcpxDeductionRuleMapper deductionRuleMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	public IPage<PromotionLinkViews.Item> list(Page<OcpxPromotionLink> page, String keyword, String advertiserId,
			String promotionLinkId, Integer platform) {
		return linkMapper
			.selectPage(page,
					Wrappers.<OcpxPromotionLink>lambdaQuery()
						.eq(StringUtils.hasText(promotionLinkId), OcpxPromotionLink::getId, promotionLinkId)
						.and(StringUtils.hasText(keyword) && !StringUtils.hasText(promotionLinkId),
								q -> q.like(OcpxPromotionLink::getId, keyword)
									.or()
									.like(OcpxPromotionLink::getLinkName, keyword))
						.eq(StringUtils.hasText(advertiserId), OcpxPromotionLink::getAdvertiserId, advertiserId)
						.eq(platform != null, OcpxPromotionLink::getPlatform, platform)
						.orderByDesc(OcpxPromotionLink::getUpdateTime))
			.convert(this::view);
	}

	public List<PromotionLinkViews.AdvertiserOption> advertiserOptions(String keyword) {
		return advertiserMapper
			.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery()
				.and(StringUtils.hasText(keyword),
						q -> q.like(OcpxAdvertiser::getId, keyword)
							.or()
							.like(OcpxAdvertiser::getAdvertiserName, keyword))
				.orderByAsc(OcpxAdvertiser::getAdvertiserName))
			.stream()
			.map(a -> new PromotionLinkViews.AdvertiserOption(a.getId(), a.getAdvertiserName()))
			.toList();
	}

	/** 对齐 SGYZ getLinkName：按广告主过滤推广链接下拉。 */
	public List<PromotionLinkViews.Option> options(String advertiserId) {
		return linkMapper
			.selectList(Wrappers.<OcpxPromotionLink>lambdaQuery()
				.eq(StringUtils.hasText(advertiserId), OcpxPromotionLink::getAdvertiserId, advertiserId)
				.orderByDesc(OcpxPromotionLink::getId))
			.stream()
			.map(item -> new PromotionLinkViews.Option(item.getId(), item.getLinkName()))
			.toList();
	}

	public PromotionLinkViews.Item detail(String id) {
		return view(require(id));
	}

	@Transactional
	public PromotionLinkViews.Item create(PromotionLinkRequests.Create request) {
		requireAdvertiser(request.advertiserId());
		OcpxPromotionLink link = new OcpxPromotionLink();
		apply(link, request.advertiserId(), request.linkName(), request.platform(), request.linkChannel(),
				request.linkUrl(), request.deeplink(), request.linkExt(), request.remark());
		link.setDeleted(false);
		linkMapper.insert(link);
		configRefreshPublisher.mark();
		return detail(link.getId());
	}

	@Transactional
	public PromotionLinkViews.Item update(String id, PromotionLinkRequests.Update request) {
		OcpxPromotionLink current = require(id);
		requireAdvertiser(request.advertiserId());
		if (!current.getAdvertiserId().equals(request.advertiserId()) && taskMapper
			.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery().eq(OcpxMonitorTask::getPromotionLinkId, id)) > 0) {
			throw new IllegalArgumentException("已被监测任务引用的推广链接不能更换广告主");
		}
		OcpxPromotionLink update = new OcpxPromotionLink();
		update.setId(id);
		apply(update, request.advertiserId(), request.linkName(), request.platform(), request.linkChannel(),
				request.linkUrl(), request.deeplink(), request.linkExt(), request.remark());
		linkMapper.updateById(update);
		configRefreshPublisher.mark();
		return detail(id);
	}

	@Transactional
	public void delete(String id) {
		require(id);
		if (taskMapper
			.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery().eq(OcpxMonitorTask::getPromotionLinkId, id)) > 0) {
			throw new IllegalArgumentException("推广链接仍被监测任务引用");
		}
		if (deductionRuleMapper
			.selectCount(Wrappers.<OcpxDeductionRule>lambdaQuery().eq(OcpxDeductionRule::getPromotionLinkId, id)) > 0) {
			throw new IllegalArgumentException("推广链接仍被扣量规则引用");
		}
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

	private void requireAdvertiser(String id) {
		if (advertiserMapper.selectById(id) == null)
			throw new IllegalArgumentException("广告主不存在");
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

	private PromotionLinkViews.Item view(OcpxPromotionLink link) {
		OcpxAdvertiser a = advertiserMapper.selectById(link.getAdvertiserId());
		return new PromotionLinkViews.Item(link.getId(), link.getAdvertiserId(),
				a == null ? null : a.getAdvertiserName(), link.getLinkName(), link.getPlatform(), link.getLinkChannel(),
				link.getLinkUrl(), link.getDeeplink(), link.getLinkExt(), link.getRemark(), link.getCreateTime(),
				link.getUpdateTime());
	}

}
