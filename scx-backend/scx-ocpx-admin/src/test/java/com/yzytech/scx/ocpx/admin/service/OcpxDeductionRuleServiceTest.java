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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

	private ValueOperations<String, String> values;

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
		values = mock(ValueOperations.class);
		configRefreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		advertiserScope = mock(AdvertiserScope.class);
		when(redis.opsForValue()).thenReturn(values);
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
		verify(values).set("ocpx:route:ratio:link-1:media-1", "0.6");
		verify(redis).delete("ocpx:route:original-count:link-1:media-1");
		verify(redis).delete("ocpx:route:delivered-count:link-1:media-1");
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
		verify(values).set("ocpx:route:ratio:link-1:media-1", "0.3");
		verify(redis).delete("ocpx:route:original-count:link-1:media-1");
		verify(redis).delete("ocpx:route:delivered-count:link-1:media-1");
		verify(configRefreshPublisher).mark();
	}

	@Test
	void updateResetsThePreviousRouteWhenDimensionsChange() {
		when(ruleMapper.selectById("rule-1")).thenReturn(rule());
		when(ruleMapper.selectCount(any())).thenReturn(0L);
		when(mediaMapper.selectById(any())).thenReturn(media());
		when(linkMapper.selectById(any())).thenReturn(link());

		service.update("rule-1", new DeductionRuleRequests.Update("media-2", "link-2", 0.4));

		verify(values).set("ocpx:route:ratio:link-1:media-1", "1.0");
		verify(redis).delete("ocpx:route:original-count:link-1:media-1");
		verify(redis).delete("ocpx:route:delivered-count:link-1:media-1");
		verify(values).set("ocpx:route:ratio:link-2:media-2", "0.4");
		verify(redis).delete("ocpx:route:original-count:link-2:media-2");
		verify(redis).delete("ocpx:route:delivered-count:link-2:media-2");
	}

	@Test
	void deleteResetsRatioToFullDeliveryAndClearsRouteCounts() {
		when(ruleMapper.selectById("rule-1")).thenReturn(rule());

		service.delete("rule-1");

		verify(ruleMapper).deleteById("rule-1");
		verify(values).set("ocpx:route:ratio:link-1:media-1", "1.0");
		verify(redis).delete("ocpx:route:original-count:link-1:media-1");
		verify(redis).delete("ocpx:route:delivered-count:link-1:media-1");
		verify(configRefreshPublisher).mark();
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
