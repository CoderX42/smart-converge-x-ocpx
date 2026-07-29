package com.yzytech.scx.inventory;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InventoryModuleTest {

	@Test
	void moduleCanLoad() {
		assertThat("inventory").isNotEmpty();
	}

}
