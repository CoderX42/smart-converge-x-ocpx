package com.yzytech.scx.commercial.service;

import com.yzytech.scx.commercial.entity.AdAccount;
import com.yzytech.scx.commercial.port.CommercialAccountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommercialAccountPortService implements CommercialAccountPort {

	private final CommercialService commercialService;

	@Override
	public CommercialAccountReference requireAccount(String adAccountId) {
		AdAccount account = commercialService.requireAdAccount(adAccountId);
		String responsible = account.getResponsibleOrgId() == null ? account.getAdvertiserOrgId()
				: account.getResponsibleOrgId();
		return new CommercialAccountReference(account.getId(), account.getAdvertiserOrgId(), responsible,
				account.getStatus());
	}

}
