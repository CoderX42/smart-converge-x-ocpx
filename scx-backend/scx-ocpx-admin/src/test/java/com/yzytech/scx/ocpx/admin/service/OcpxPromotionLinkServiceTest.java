package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkViews;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxPromotionLinkServiceTest {

	private OcpxPromotionLinkMapper linkMapper;

	private OcpxAdvertiserMapper advertiserMapper;

	private OcpxMonitorTaskMapper taskMapper;

	private OcpxDeductionRuleMapper deductionRuleMapper;

	private OcpxConfigRefreshPublisher refreshPublisher;

	private AdvertiserScope advertiserScope;

	private OcpxPromotionLinkService service;

	@BeforeEach
	void setUp() {
		linkMapper = mock(OcpxPromotionLinkMapper.class);
		taskMapper = mock(OcpxMonitorTaskMapper.class);
		deductionRuleMapper = mock(OcpxDeductionRuleMapper.class);
		refreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		advertiserMapper = mock(OcpxAdvertiserMapper.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxPromotionLinkService(linkMapper, advertiserMapper, taskMapper, deductionRuleMapper,
				refreshPublisher, advertiserScope);
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-1");
		when(linkMapper.selectById("link-1")).thenReturn(link);
	}

	@Test
	void createPersistsSubmittedUrlsWithoutHttpValidation() {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("adv-1");
		advertiser.setAdvertiserName("广告主一");
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser);
		AtomicReference<OcpxPromotionLink> stored = new AtomicReference<>();
		doAnswer(invocation -> {
			OcpxPromotionLink link = invocation.getArgument(0);
			link.setId("link-9");
			stored.set(link);
			return 1;
		}).when(linkMapper).insert(any(OcpxPromotionLink.class));
		when(linkMapper.selectById("link-9")).thenAnswer(invocation -> stored.get());

		PromotionLinkViews.Item result = service.create(new PromotionLinkRequests.Create("adv-1", "链接一", 1, "ch-1",
				"https://app.example/download", "tbopen://m.taobao.com/tbopen/index.html", "k=v", "备注"));

		assertThat(result.getLinkUrl()).isEqualTo("https://app.example/download");
		assertThat(result.getDeeplink()).isEqualTo("tbopen://m.taobao.com/tbopen/index.html");
		assertThat(result.getLinkExt()).isEqualTo("k=v");
		assertThat(result.getRemark()).isEqualTo("备注");
		verify(refreshPublisher).mark();
	}

	@Test
	void gravityRequiresOneAndroidIosBaseLinkWithoutDynamicParameters() {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("adv-1");
		advertiser.setUpstreamProtocol(OcpxUpstreamProtocol.GRAVITY);
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser);

		assertThatThrownBy(() -> service.create(new PromotionLinkRequests.Create("adv-1", "引力链接", 1, null,
				"https://backend.gravity-engine.com/event_center/api/v1/event/click/", null, null, null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("Android+IOS");
		assertThatThrownBy(() -> service.create(new PromotionLinkRequests.Create("adv-1", "引力链接", 3, null,
				"https://backend.gravity-engine.com/event_center/api/v1/event/click/?idfa={idfa}", null, null, null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("无动态宏");
		assertThatThrownBy(() -> service.create(new PromotionLinkRequests.Create("adv-1", "引力链接", 3, null,
				"https://backend.gravity-engine.com/event_center/api/v1/event/click/?callback=x", null, null, null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不能预置");
		verify(linkMapper, never()).insert(any(OcpxPromotionLink.class));
	}

	@Test
	void optionsReturnLinksForAdvertiserCascade() {
		OcpxPromotionLink first = new OcpxPromotionLink();
		first.setId("link-1");
		first.setLinkName("链接一");
		when(linkMapper.selectList(any())).thenReturn(List.of(first));

		assertThat(service.options("adv-1"))
			.extracting(PromotionLinkViews.Option::getId, PromotionLinkViews.Option::getName)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("link-1", "链接一"));
	}

	@Test
	void deleteRejectsMonitorTaskDependency() {
		when(taskMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.delete("link-1")).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("监测任务");
		verify(linkMapper, never()).deleteById("link-1");
	}

	@Test
	void deleteRejectsDeductionRuleDependency() {
		when(taskMapper.selectCount(any())).thenReturn(0L);
		when(deductionRuleMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.delete("link-1")).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("扣量规则");
		verify(linkMapper, never()).deleteById("link-1");
	}

	@Test
	void deleteUsesLogicalDeleteAndRefreshesIndex() {
		when(taskMapper.selectCount(any())).thenReturn(0L);
		when(deductionRuleMapper.selectCount(any())).thenReturn(0L);

		service.delete("link-1");

		verify(linkMapper).deleteById("link-1");
		verify(refreshPublisher).mark();
	}

}
