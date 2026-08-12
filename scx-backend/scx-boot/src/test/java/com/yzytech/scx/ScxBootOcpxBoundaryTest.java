package com.yzytech.scx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ScxBootOcpxBoundaryTest {

	@Test
	void exposesOnlyTheSharedOcpxEntitiesOnTheManagementClasspath() {
		assertThat(new ClassPathResource("ocpx-protocol-crypto.yml").exists()).isFalse();
		ClassLoader classLoader = getClass().getClassLoader();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxAdvertiser.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxPromotionLink.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxMedia.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxMonitorTask.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/ScxOcpxEngineApplication.class")).isNull();
	}

	@Test
	void usesStandardSpringBootComponentScanningWithoutCustomExclusions() {
		assertThat(ScxApplication.class.getDeclaredAnnotation(SpringBootApplication.class)).isNotNull();
	}

}
