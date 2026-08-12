package com.yzytech.scx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ScxBootOcpxBoundaryTest {

	@Test
	void exposesTheSharedOcpxEntitiesAndEngineEntrypointOnTheClasspath() {
		assertThat(new ClassPathResource("ocpx-protocol-crypto.yml").exists()).isFalse();
		ClassLoader classLoader = getClass().getClassLoader();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxAdvertiser.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxPromotionLink.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxMedia.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/entity/OcpxMonitorTask.class")).isNotNull();
		assertThat(classLoader.getResource("com/yzytech/scx/ocpx/ScxOcpxEngineApplication.class")).isNotNull();
	}

	@Test
	void keepsEngineRuntimeBeansOutOfTheManagementApplication() {
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
			assertThat(filter.pattern()).containsExactly(
					"com\\.yzytech\\.scx\\.ocpx\\.(?:ScxOcpxEngineApplication|(?!(?:entity|admin)(?:\\.|$)).*)");
			Pattern engineRuntimePattern = Pattern.compile(filter.pattern()[0]);
			assertThat(engineRuntimePattern.matcher("com.yzytech.scx.ocpx.ScxOcpxEngineApplication").matches())
				.isTrue();
			assertThat(engineRuntimePattern.matcher("com.yzytech.scx.ocpx.task.StorageTask").matches()).isTrue();
			assertThat(engineRuntimePattern.matcher("com.yzytech.scx.ocpx.entity.OcpxAdvertiser").matches()).isFalse();
			assertThat(engineRuntimePattern.matcher("com.yzytech.scx.ocpx.admin.controller.OcpxAdvertiserController")
				.matches()).isFalse();
		});
	}

}
