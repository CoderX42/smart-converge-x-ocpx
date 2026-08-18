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
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

	private static final String ROUTE_DEDUCTION_PREFIX = "ocpx:route:deduction:";

	private static final String ROUTE_DEDUCTION_RATIO_FIELD = "ratio";

	private static final DefaultRedisScript<Long> RESET_ROUTE_SCRIPT = new DefaultRedisScript<>("""
			redis.call('DEL', KEYS[1])
			redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
			return 1
			""", Long.class);

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
		requireMedia(request.getMediaId());
		requirePromotionLink(request.getPromotionLinkId());
		ensureUnique(null, request.getMediaId(), request.getPromotionLinkId());
		OcpxDeductionRule rule = new OcpxDeductionRule();
		rule.setMediaId(request.getMediaId());
		rule.setPromotionLinkId(request.getPromotionLinkId());
		rule.setRatio(request.getRatio());
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
		requireMedia(request.getMediaId());
		requirePromotionLink(request.getPromotionLinkId());
		ensureUnique(id, request.getMediaId(), request.getPromotionLinkId());
		OcpxDeductionRule update = new OcpxDeductionRule();
		update.setId(id);
		update.setMediaId(request.getMediaId());
		update.setPromotionLinkId(request.getPromotionLinkId());
		update.setRatio(request.getRatio());
		ruleMapper.updateById(update);
		if (!current.getMediaId().equals(request.getMediaId())
				|| !current.getPromotionLinkId().equals(request.getPromotionLinkId())) {
			syncRouteRatio(current.getPromotionLinkId(), current.getMediaId(), 1.0);
		}
		syncRouteRatio(request.getPromotionLinkId(), request.getMediaId(), request.getRatio());
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

	/** 写媒体回传比例并重置该路由的实时扣量状态。 */
	private void syncRouteRatio(String promotionLinkId, String mediaId, double ratio) {
		String route = promotionLinkId + ":" + mediaId;
		String stateKey = atomicStateKey(promotionLinkId, mediaId);
		redis.execute(RESET_ROUTE_SCRIPT, java.util.List.of(stateKey), ROUTE_DEDUCTION_RATIO_FIELD,
				String.valueOf(ratio));
		redis.delete(java.util.List.of(ROUTE_RATIO_PREFIX + route, ROUTE_ORIGINAL_COUNT_PREFIX + route,
				ROUTE_DELIVERED_COUNT_PREFIX + route));
	}

	private String atomicStateKey(String promotionLinkId, String mediaId) {
		return ROUTE_DEDUCTION_PREFIX + "{" + promotionLinkId + ":" + mediaId + "}:state";
	}

	private void requireMedia(String id) {
		if (mediaMapper.selectById(id) == null)
			throw new BootBusinessException("媒体不存在");
	}

	private void requirePromotionLink(String id) {
		if (linkMapper.selectById(id) == null)
			throw new BootBusinessException("推广链接不存在");
	}

	private void ensureUnique(String id, String mediaId, String promotionLinkId) {
		if (ruleMapper.selectCount(Wrappers.<OcpxDeductionRule>lambdaQuery()
			.eq(OcpxDeductionRule::getMediaId, mediaId)
			.eq(OcpxDeductionRule::getPromotionLinkId, promotionLinkId)
			.ne(StringUtils.hasText(id), OcpxDeductionRule::getId, id)) > 0)
			throw new BootBusinessException("该媒体与推广链接的扣量规则已存在");
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
		return new DeductionRuleViews.Item(rule.getId(), rule.getMediaId(), media == null ? null : media.getMediaName(),
				rule.getPromotionLinkId(), link == null ? null : link.getLinkName(), rule.getRatio(),
				rule.getCreateTime(), rule.getUpdateTime());
	}

}
