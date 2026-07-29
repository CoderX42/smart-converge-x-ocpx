package com.yzytech.scx.commercial.port;

public interface CommercialAccountPort {

	CommercialAccountReference requireAccount(String adAccountId);

	record CommercialAccountReference(String id, String advertiserOrganizationId, String responsibleOrganizationId,
			String status) {
	}

}
