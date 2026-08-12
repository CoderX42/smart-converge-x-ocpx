package com.yzytech.scx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ScxBootOcpxBoundaryTest {

	@Test
	void exposesTheSharedOcpxModelAndEngineEntrypointOnTheClasspath() {
		assertThat(new ClassPathResource("ocpx-protocol-crypto.yml").exists()).isFalse();
		ClassLoader classLoader = getClass().getClassLoader();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxAdvertiser.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxPromotionLink.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxMedia.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxMonitorTask.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/ScxOcpxEngineApplication.class")).isNotNull();
	}

	@Test
	void keepsTheStandardManagementComponentScanFilters() {
		ComponentScan componentScan = ScxApplication.class.getDeclaredAnnotation(ComponentScan.class);

		assertThat(componentScan).isNotNull();
		assertThat(Arrays.asList(componentScan.excludeFilters())).anySatisfy(filter -> {
			assertThat(filter.type()).isEqualTo(FilterType.CUSTOM);
			assertThat(filter.classes()).contains(TypeExcludeFilter.class);
		});
		assertThat(Arrays.asList(componentScan.excludeFilters())).anySatisfy(filter -> {
			assertThat(filter.type()).isEqualTo(FilterType.CUSTOM);
			assertThat(filter.classes()).contains(AutoConfigurationExcludeFilter.class);
		});
		assertThat(Arrays.asList(componentScan.excludeFilters())).anySatisfy(filter -> {
			assertThat(filter.type()).isEqualTo(FilterType.REGEX);
			assertThat(filter.pattern())
				.containsExactly("com\\.yzytech\\.scx\\.ocpx\\.ScxOcpxEngineApplication");
		});
	}

}
