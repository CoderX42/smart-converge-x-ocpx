package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.DeductionRuleRequests;
import com.yzytech.scx.ocpx.admin.dto.DeductionRuleViews;
import com.yzytech.scx.ocpx.entity.OcpxDeductionRule;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OcpxDeductionRuleService {

	/** 与 scx-ocpx-engine 的 RedisConstants 字面量保持一致。 */
	private static final String ROUTE_RATIO_PREFIX = "ocpx:route:ratio:";

	private static final String ROUTE_ORIGINAL_COUNT_PREFIX = "ocpx:route:original-count:";

	private static final String ROUTE_DELIVERED_COUNT_PREFIX = "ocpx:route:delivered-count:";

	private final OcpxDeductionRuleMapper ruleMapper;

	private final OcpxMediaMapper mediaMapper;

	private final OcpxPromotionLinkMapper linkMapper;

	private final StringRedisTemplate redis;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final AdvertiserScope advertiserScope;

	public IPage<DeductionRuleViews.Item> list(Page<OcpxDeductionRule> page, String promotionLinkId) {
		advertiserScope.requireAdministrator();
		return ruleMapper
			.selectPage(page,
					Wrappers.<OcpxDeductionRule>lambdaQuery()
						.eq(StringUtils.hasText(promotionLinkId), OcpxDeductionRule::getPromotionLinkId,
								promotionLinkId)
						.orderByDesc(OcpxDeductionRule::getUpdateTime))
			.convert(this::view);
	}

	public DeductionRuleViews.Item detail(String id) {
		advertiserScope.requireAdministrator();
		return view(require(id));
	}

	@Transactional
	public DeductionRuleViews.Item create(DeductionRuleRequests.Create request) {
		advertiserScope.requireAdministrator();
		requireMedia(request.mediaId());
		requirePromotionLink(request.promotionLinkId());
		ensureUnique(null, request.mediaId(), request.promotionLinkId());
		OcpxDeductionRule rule = new OcpxDeductionRule();
		rule.setMediaId(request.mediaId());
		rule.setPromotionLinkId(request.promotionLinkId());
		rule.setRatio(request.ratio());
		rule.setDeleted(false);
		ruleMapper.insert(rule);
		syncRouteRatio(rule.getPromotionLinkId(), rule.getMediaId(), rule.getRatio());
		configRefreshPublisher.mark();
		return detail(rule.getId());
	}

	@Transactional
	public DeductionRuleViews.Item update(String id, DeductionRuleRequests.Update request) {
		advertiserScope.requireAdministrator();
		OcpxDeductionRule current = require(id);
		requireMedia(request.mediaId());
		requirePromotionLink(request.promotionLinkId());
		ensureUnique(id, request.mediaId(), request.promotionLinkId());
		OcpxDeductionRule update = new OcpxDeductionRule();
		update.setId(id);
		update.setMediaId(request.mediaId());
		update.setPromotionLinkId(request.promotionLinkId());
		update.setRatio(request.ratio());
		ruleMapper.updateById(update);
		if (!current.getMediaId().equals(request.mediaId())
				|| !current.getPromotionLinkId().equals(request.promotionLinkId())) {
			syncRouteRatio(current.getPromotionLinkId(), current.getMediaId(), 1.0);
		}
		syncRouteRatio(request.promotionLinkId(), request.mediaId(), request.ratio());
		configRefreshPublisher.mark();
		return detail(id);
	}

	@Transactional
	public void delete(String id) {
		advertiserScope.requireAdministrator();
		OcpxDeductionRule current = require(id);
		ruleMapper.deleteById(id);
		syncRouteRatio(current.getPromotionLinkId(), current.getMediaId(), 1.0);
		configRefreshPublisher.mark();
	}

	/** 写比例并重置该路由的原始/实际回传计数（平移 SGYZ 改比例清零计数的行为）。 */
	private void syncRouteRatio(String promotionLinkId, String mediaId, double ratio) {
		String route = promotionLinkId + ":" + mediaId;
		redis.opsForValue().set(ROUTE_RATIO_PREFIX + route, String.valueOf(ratio));
		redis.delete(ROUTE_ORIGINAL_COUNT_PREFIX + route);
		redis.delete(ROUTE_DELIVERED_COUNT_PREFIX + route);
	}

	private void requireMedia(String id) {
		if (mediaMapper.selectById(id) == null)
			throw new IllegalArgumentException("媒体不存在");
	}

	private void requirePromotionLink(String id) {
		if (linkMapper.selectById(id) == null)
			throw new IllegalArgumentException("推广链接不存在");
	}

	private void ensureUnique(String id, String mediaId, String promotionLinkId) {
		if (ruleMapper.selectCount(Wrappers.<OcpxDeductionRule>lambdaQuery()
			.eq(OcpxDeductionRule::getMediaId, mediaId)
			.eq(OcpxDeductionRule::getPromotionLinkId, promotionLinkId)
			.ne(StringUtils.hasText(id), OcpxDeductionRule::getId, id)) > 0)
			throw new IllegalArgumentException("该媒体与推广链接的扣量规则已存在");
	}

	private OcpxDeductionRule require(String id) {
		OcpxDeductionRule value = ruleMapper.selectById(id);
		if (value == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "扣量规则不存在");
		return value;
	}

	private DeductionRuleViews.Item view(OcpxDeductionRule rule) {
		OcpxMedia media = mediaMapper.selectById(rule.getMediaId());
		OcpxPromotionLink link = linkMapper.selectById(rule.getPromotionLinkId());
		return new DeductionRuleViews.Item(rule.getId(), rule.getMediaId(),
				media == null ? null : media.getMediaName(), rule.getPromotionLinkId(),
				link == null ? null : link.getLinkName(), rule.getRatio(), rule.getCreateTime(),
				rule.getUpdateTime());
	}

}
