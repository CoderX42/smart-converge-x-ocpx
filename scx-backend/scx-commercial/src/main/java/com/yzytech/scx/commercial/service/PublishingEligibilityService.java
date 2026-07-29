package com.yzytech.scx.commercial.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.commercial.entity.AdAccount;
import com.yzytech.scx.commercial.entity.Advertiser;
import com.yzytech.scx.commercial.entity.Organization;
import com.yzytech.scx.commercial.mapper.AdvertiserMapper;
import com.yzytech.scx.commercial.mapper.OrganizationMapper;
import com.yzytech.scx.commercial.port.PublishingEligibilityPort;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublishingEligibilityService implements PublishingEligibilityPort {

	private final CommercialService commercialService;

	private final AgreementService agreementService;

	private final AdvertiserMapper advertiserMapper;

	private final OrganizationMapper organizationMapper;

	@Override
	public PublishingEligibility evaluate(String adAccountId) {
		AdAccount account = commercialService.requireAdAccount(adAccountId);
		List<String> reasons = new ArrayList<>();
		if (!"ACTIVE".equals(account.getStatus())) {
			reasons.add("AD_ACCOUNT_NOT_ACTIVE");
		}
		Organization organization = organizationMapper.selectById(account.getAdvertiserOrgId());
		if (organization == null || !"ACTIVE".equals(organization.getStatus())) {
			reasons.add("ORGANIZATION_NOT_ACTIVE");
		}
		Advertiser advertiser = advertiserMapper
			.selectOne(Wrappers.<Advertiser>lambdaQuery().eq(Advertiser::getOrgId, account.getAdvertiserOrgId()));
		if (advertiser == null || !"ACTIVE".equals(advertiser.getStatus())) {
			reasons.add("ADVERTISER_NOT_ACTIVE");
		}
		if (!commercialService.hasApprovedQualification(account.getAdvertiserOrgId())) {
			reasons.add("QUALIFICATION_NOT_APPROVED");
		}
		if (!agreementService.hasActiveAgreement(account.getAdvertiserOrgId())) {
			reasons.add("AGREEMENT_NOT_ACTIVE");
		}
		String responsibleOrganizationId = account.getResponsibleOrgId() == null ? account.getAdvertiserOrgId()
				: account.getResponsibleOrgId();
		if (!agreementService.hasActiveAuthorization(account.getAdvertiserOrgId(), responsibleOrganizationId)) {
			reasons.add("DATA_AUTHORIZATION_NOT_ACTIVE");
		}
		return new PublishingEligibility(reasons.isEmpty(), List.copyOf(reasons));
	}

}
