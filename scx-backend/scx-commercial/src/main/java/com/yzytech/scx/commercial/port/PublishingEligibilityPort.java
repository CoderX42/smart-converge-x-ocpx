package com.yzytech.scx.commercial.port;

import java.util.List;

public interface PublishingEligibilityPort {

	PublishingEligibility evaluate(String adAccountId);

	record PublishingEligibility(boolean eligible, List<String> reasonCodes) {
	}

}
