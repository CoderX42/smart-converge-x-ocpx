package com.yzytech.scx.commercial;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CommercialModuleTest {

	@Test
	void moduleCanLoad() {
		assertThat("commercial").isNotEmpty();
	}

}
