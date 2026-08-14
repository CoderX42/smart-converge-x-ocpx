package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.PromotionLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.PromotionLinkViews;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
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

		assertThat(result.linkUrl()).isEqualTo("https://app.example/download");
		assertThat(result.deeplink()).isEqualTo("tbopen://m.taobao.com/tbopen/index.html");
		assertThat(result.linkExt()).isEqualTo("k=v");
		assertThat(result.remark()).isEqualTo("备注");
		verify(refreshPublisher).mark();
	}

	@Test
	void optionsReturnLinksForAdvertiserCascade() {
		OcpxPromotionLink first = new OcpxPromotionLink();
		first.setId("link-1");
		first.setLinkName("链接一");
		when(linkMapper.selectList(any())).thenReturn(List.of(first));

		assertThat(service.options("adv-1")).extracting(PromotionLinkViews.Option::id, PromotionLinkViews.Option::name)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("link-1", "链接一"));
	}

	@Test
	void deleteRejectsMonitorTaskDependency() {
		when(taskMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.delete("link-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("监测任务");
		verify(linkMapper, never()).deleteById("link-1");
	}

	@Test
	void deleteRejectsDeductionRuleDependency() {
		when(taskMapper.selectCount(any())).thenReturn(0L);
		when(deductionRuleMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.delete("link-1"))
			.isInstanceOf(IllegalArgumentException.class)
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
