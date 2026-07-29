package com.yzytech.scx.creative;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CreativeModuleTest {

	@Test
	void moduleCanLoad() {
		assertThat("creative").isNotEmpty();
	}

}
