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
	void exposesRequiredOcpxProtocolCryptoConfigurationOnBootClasspath() {
		assertThat(new ClassPathResource("ocpx-protocol-crypto.yml").exists()).isTrue();
	}

	@Test
	void excludesTheIndependentOcpxDataPlaneFromManagementComponentScanning() {
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
			assertThat(filter.pattern()).containsExactly("com\\.yzytech\\.scx\\.ocpx\\.runtime\\..*");
		});
	}

}
