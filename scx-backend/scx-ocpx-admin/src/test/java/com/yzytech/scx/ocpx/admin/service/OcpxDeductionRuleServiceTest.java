package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.DeductionRuleRequests;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.entity.OcpxDeductionRule;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OcpxDeductionRuleServiceTest {

	private OcpxDeductionRuleMapper ruleMapper;

	private OcpxMediaMapper mediaMapper;

	private OcpxPromotionLinkMapper linkMapper;

	private StringRedisTemplate redis;

	private OcpxConfigRefreshPublisher configRefreshPublisher;

	private AdvertiserScope advertiserScope;

	private OcpxDeductionRuleService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		ruleMapper = mock(OcpxDeductionRuleMapper.class);
		mediaMapper = mock(OcpxMediaMapper.class);
		linkMapper = mock(OcpxPromotionLinkMapper.class);
		redis = mock(StringRedisTemplate.class);
		configRefreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxDeductionRuleService(ruleMapper, mediaMapper, linkMapper, redis, configRefreshPublisher,
				advertiserScope);
	}

	@Test
	void advertiserAccountIsRejectedByEveryDeductionEntryPoint() {
		doThrow(new BootBusinessException(403, "广告主账号无权执行该操作")).when(advertiserScope).requireAdministrator();

		assertThatThrownBy(() -> service.list(new Page<>(1, 10), null)).isInstanceOf(BootBusinessException.class);
		assertThatThrownBy(() -> service.detail("rule-1")).isInstanceOf(BootBusinessException.class);
		assertThatThrownBy(() -> service.create(new DeductionRuleRequests.Create("media-1", "link-1", 0.6)))
			.isInstanceOf(BootBusinessException.class);
		assertThatThrownBy(() -> service.update("rule-1", new DeductionRuleRequests.Update("media-1", "link-1", 0.6)))
			.isInstanceOf(BootBusinessException.class);
		assertThatThrownBy(() -> service.delete("rule-1")).isInstanceOf(BootBusinessException.class);
		verifyNoInteractions(ruleMapper, mediaMapper, linkMapper, redis);
	}

	@Test
	void createWritesRatioAndResetsRouteCounts() {
		when(ruleMapper.selectCount(any())).thenReturn(0L);
		when(ruleMapper.selectById(any())).thenReturn(rule());
		when(mediaMapper.selectById(any())).thenReturn(media());
		when(linkMapper.selectById(any())).thenReturn(link());

		service.create(new DeductionRuleRequests.Create("media-1", "link-1", 0.6));

		verify(ruleMapper).insert(any(OcpxDeductionRule.class));
		verifyAtomicRatioReset("link-1", "media-1", "0.6");
		verifyLegacyKeysDeleted("link-1:media-1");
		verify(configRefreshPublisher).mark();
	}

	@Test
	void updateRewritesRatioAndResetsRouteCounts() {
		when(ruleMapper.selectById("rule-1")).thenReturn(rule());
		when(ruleMapper.selectCount(any())).thenReturn(0L);
		when(mediaMapper.selectById(any())).thenReturn(media());
		when(linkMapper.selectById(any())).thenReturn(link());

		service.update("rule-1", new DeductionRuleRequests.Update("media-1", "link-1", 0.3));

		verify(ruleMapper).updateById(any(OcpxDeductionRule.class));
		verifyAtomicRatioReset("link-1", "media-1", "0.3");
		verifyLegacyKeysDeleted("link-1:media-1");
		verify(configRefreshPublisher).mark();
	}

	@Test
	void updateResetsThePreviousRouteWhenDimensionsChange() {
		when(ruleMapper.selectById("rule-1")).thenReturn(rule());
		when(ruleMapper.selectCount(any())).thenReturn(0L);
		when(mediaMapper.selectById(any())).thenReturn(media());
		when(linkMapper.selectById(any())).thenReturn(link());

		service.update("rule-1", new DeductionRuleRequests.Update("media-2", "link-2", 0.4));

		verifyAtomicRatioReset("link-1", "media-1", "1.0");
		verifyLegacyKeysDeleted("link-1:media-1");
		verifyAtomicRatioReset("link-2", "media-2", "0.4");
		verifyLegacyKeysDeleted("link-2:media-2");
	}

	@Test
	void deleteResetsRatioToFullDeliveryAndClearsRouteCounts() {
		when(ruleMapper.selectById("rule-1")).thenReturn(rule());

		service.delete("rule-1");

		verify(ruleMapper).deleteById("rule-1");
		verifyAtomicRatioReset("link-1", "media-1", "1.0");
		verifyLegacyKeysDeleted("link-1:media-1");
		verify(configRefreshPublisher).mark();
	}

	@SuppressWarnings("unchecked")
	private void verifyLegacyKeysDeleted(String route) {
		verify(redis).delete(org.mockito.ArgumentMatchers
			.argThat((Collection<String> keys) -> keys.containsAll(java.util.List.of("ocpx:route:ratio:" + route,
					"ocpx:route:original-count:" + route, "ocpx:route:delivered-count:" + route))));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void verifyAtomicRatioReset(String promotionLinkId, String mediaId, String ratio) {
		verify(redis).execute(any(RedisScript.class),
				eq(java.util.List.of("ocpx:route:deduction:{" + promotionLinkId + ":" + mediaId + "}:state")),
				eq("ratio"), eq(ratio));
	}

	@Test
	void createRejectsDuplicatedActiveRule() {
		when(mediaMapper.selectById("media-1")).thenReturn(media());
		when(linkMapper.selectById("link-1")).thenReturn(link());
		when(ruleMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.create(new DeductionRuleRequests.Create("media-1", "link-1", 0.6)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("已存在");
		verifyNoInteractions(redis);
	}

	@Test
	void createRejectsMissingMediaAndPromotionLink() {
		when(mediaMapper.selectById("media-1")).thenReturn(null);
		assertThatThrownBy(() -> service.create(new DeductionRuleRequests.Create("media-1", "link-1", 0.6)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("媒体");

		when(mediaMapper.selectById("media-1")).thenReturn(media());
		when(linkMapper.selectById("link-1")).thenReturn(null);
		assertThatThrownBy(() -> service.create(new DeductionRuleRequests.Create("media-1", "link-1", 0.6)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("推广链接");
		verifyNoInteractions(redis);
	}

	@Test
	void apiRatioValidationAcceptsOnlyZeroThroughOne() {
		var validator = Validation.buildDefaultValidatorFactory().getValidator();

		assertThat(validator.validate(new DeductionRuleRequests.Create("media-1", "link-1", 0.0))).isEmpty();
		assertThat(validator.validate(new DeductionRuleRequests.Create("media-1", "link-1", 1.0))).isEmpty();
		assertThat(validator.validate(new DeductionRuleRequests.Create("media-1", "link-1", -0.01))).isNotEmpty();
		assertThat(validator.validate(new DeductionRuleRequests.Create("media-1", "link-1", 1.01))).isNotEmpty();
	}

	private OcpxDeductionRule rule() {
		OcpxDeductionRule rule = new OcpxDeductionRule();
		rule.setId("rule-1");
		rule.setMediaId("media-1");
		rule.setPromotionLinkId("link-1");
		rule.setRatio(0.6);
		rule.setDeleted(false);
		return rule;
	}

	private OcpxMedia media() {
		OcpxMedia media = new OcpxMedia();
		media.setId("media-1");
		media.setMediaName("媒体一");
		return media;
	}

	private OcpxPromotionLink link() {
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-1");
		link.setLinkName("链接一");
		return link;
	}

}
