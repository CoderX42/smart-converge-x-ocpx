package com.yzytech.scx.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.campaign.entity.AdGroup;
import com.yzytech.scx.campaign.entity.Ad;
import com.yzytech.scx.campaign.entity.Campaign;
import com.yzytech.scx.campaign.mapper.AdGroupMapper;
import com.yzytech.scx.campaign.mapper.AdGroupRevisionMapper;
import com.yzytech.scx.campaign.mapper.AdMapper;
import com.yzytech.scx.campaign.mapper.AdRevisionMapper;
import com.yzytech.scx.campaign.mapper.BudgetPlanMapper;
import com.yzytech.scx.campaign.mapper.CampaignMapper;
import com.yzytech.scx.campaign.mapper.CampaignRevisionMapper;
import com.yzytech.scx.campaign.mapper.MediaPublishingBindingMapper;
import com.yzytech.scx.campaign.mapper.OptimizationGoalMapper;
import com.yzytech.scx.campaign.mapper.PublishingOperationMapper;
import com.yzytech.scx.campaign.mapper.TargetingRevisionMapper;
import com.yzytech.scx.commercial.entity.AdAccount;
import com.yzytech.scx.commercial.port.PublishingEligibilityPort;
import com.yzytech.scx.commercial.service.CommercialService;
import com.yzytech.scx.creative.service.CreativeService;
import com.yzytech.scx.finance.service.FinanceService;
import com.yzytech.scx.inventory.service.InventoryService;
import com.yzytech.scx.inventory.service.MediaAdapterService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignServiceTest {

	private final CampaignMapper campaignMapper = mock(CampaignMapper.class);

	private final AdGroupMapper adGroupMapper = mock(AdGroupMapper.class);

	private final AdMapper adMapper = mock(AdMapper.class);

	private final CommercialService commercialService = mock(CommercialService.class);

	private final CreativeService creativeService = mock(CreativeService.class);

	private final CampaignService service = new CampaignService(campaignMapper, mock(CampaignRevisionMapper.class),
			adGroupMapper, mock(AdGroupRevisionMapper.class), adMapper, mock(AdRevisionMapper.class),
			mock(OptimizationGoalMapper.class), mock(BudgetPlanMapper.class), mock(TargetingRevisionMapper.class),
			mock(MediaPublishingBindingMapper.class), mock(PublishingOperationMapper.class), commercialService,
			mock(FinanceService.class), mock(InventoryService.class), mock(MediaAdapterService.class), creativeService,
			mock(PublishingEligibilityPort.class), new ObjectMapper());

	@Test
	void campaignRejectsDailyBudgetAboveTotalBudget() {
		when(commercialService.requireWritableAdAccount("account-1")).thenReturn(account());

		assertThatThrownBy(() -> service.createCampaign("account-1", "计划", "CONVERSION", "CPC", new BigDecimal("20"),
				new BigDecimal("100"), new BigDecimal("120"), null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("日预算");
	}

	@Test
	void unavailableFormSubmitGoalIsRejected() {
		Campaign campaign = campaign();
		when(campaignMapper.selectById("campaign-1")).thenReturn(campaign);
		when(commercialService.requireAdAccount("account-1")).thenReturn(account());
		when(commercialService.requireWritableAdAccount("account-1")).thenReturn(account());

		assertThatThrownBy(() -> service.createAdGroup("campaign-1", "广告组", "FORM_SUBMIT", new BigDecimal("20"),
				new BigDecimal("100"), new BigDecimal("10"), "{}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("目标事件");
	}

	@Test
	void adCreationValidatesTheActualCreativeBinding() {
		Campaign campaign = campaign();
		AdGroup group = new AdGroup();
		group.setId("group-1");
		group.setCampaignId("campaign-1");
		when(adGroupMapper.selectById("group-1")).thenReturn(group);
		when(campaignMapper.selectById("campaign-1")).thenReturn(campaign);
		when(commercialService.requireAdAccount("account-1")).thenReturn(account());
		when(commercialService.requireWritableAdAccount("account-1")).thenReturn(account());

		service.createAd("group-1", "广告", "binding-1");

		verify(creativeService).requireBinding("binding-1", "group-1");
		verify(adMapper).insert(org.mockito.ArgumentMatchers.any(Ad.class));
	}

	private Campaign campaign() {
		Campaign campaign = new Campaign();
		campaign.setId("campaign-1");
		campaign.setAdAccountId("account-1");
		campaign.setStatus("DRAFT");
		campaign.setVersion(1);
		return campaign;
	}

	private AdAccount account() {
		AdAccount account = new AdAccount();
		account.setId("account-1");
		account.setAdvertiserOrgId("org-1");
		account.setResponsibleOrgId("org-1");
		account.setStatus("ACTIVE");
		return account;
	}

}
