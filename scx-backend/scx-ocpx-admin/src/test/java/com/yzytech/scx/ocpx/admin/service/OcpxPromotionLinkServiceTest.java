package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxPromotionLinkServiceTest {

	private OcpxPromotionLinkMapper linkMapper;

	private OcpxMonitorTaskMapper taskMapper;

	private OcpxDeductionRuleMapper deductionRuleMapper;

	private OcpxConfigRefreshPublisher refreshPublisher;

	private OcpxPromotionLinkService service;

	@BeforeEach
	void setUp() {
		linkMapper = mock(OcpxPromotionLinkMapper.class);
		taskMapper = mock(OcpxMonitorTaskMapper.class);
		deductionRuleMapper = mock(OcpxDeductionRuleMapper.class);
		refreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		service = new OcpxPromotionLinkService(linkMapper, mock(OcpxAdvertiserMapper.class), taskMapper,
				deductionRuleMapper, mock(OcpxUrlPolicy.class), refreshPublisher);
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-1");
		when(linkMapper.selectById("link-1")).thenReturn(link);
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
