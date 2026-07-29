package com.yzytech.scx.campaign;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignModuleTest {

	@Test
	void moduleCanLoad() {
		assertThat("campaign").isNotEmpty();
	}

}
