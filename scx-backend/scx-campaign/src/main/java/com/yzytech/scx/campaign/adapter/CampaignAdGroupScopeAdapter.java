package com.yzytech.scx.campaign.adapter;

import com.yzytech.scx.campaign.entity.AdGroup;
import com.yzytech.scx.campaign.entity.Campaign;
import com.yzytech.scx.campaign.mapper.AdGroupMapper;
import com.yzytech.scx.campaign.mapper.CampaignMapper;
import com.yzytech.scx.commercial.service.CommercialService;
import com.yzytech.scx.creative.port.AdGroupScopePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignAdGroupScopeAdapter implements AdGroupScopePort {

	private final AdGroupMapper adGroupMapper;

	private final CampaignMapper campaignMapper;

	private final CommercialService commercialService;

	@Override
	public void requireReadable(String adGroupId) {
		Campaign campaign = requireCampaign(adGroupId);
		commercialService.requireAdAccount(campaign.getAdAccountId());
	}

	@Override
	public void requireWritable(String adGroupId) {
		Campaign campaign = requireCampaign(adGroupId);
		commercialService.requireWritableAdAccount(campaign.getAdAccountId());
	}

	private Campaign requireCampaign(String adGroupId) {
		AdGroup group = adGroupMapper.selectById(adGroupId);
		if (group == null)
			throw new IllegalArgumentException("资源不存在");
		Campaign campaign = campaignMapper.selectById(group.getCampaignId());
		if (campaign == null)
			throw new IllegalArgumentException("资源不存在");
		return campaign;
	}

}
