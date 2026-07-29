package com.yzytech.scx.finance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FinanceModuleTest {

	@Test
	void moduleCanLoad() {
		assertThat("finance").isNotEmpty();
	}

}
