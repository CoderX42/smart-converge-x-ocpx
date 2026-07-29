package com.yzytech.scx.campaign.dto;

import com.yzytech.scx.campaign.entity.Ad;
import com.yzytech.scx.campaign.entity.AdGroup;
import com.yzytech.scx.campaign.entity.Campaign;
import com.yzytech.scx.campaign.entity.MediaPublishingBinding;
import java.util.List;

public record CampaignDetailView(Campaign campaign, List<AdGroup> adGroups, List<Ad> ads,
		List<MediaPublishingBinding> mediaBindings, List<String> validationErrors) {
}
