

package com.yzytech.scx;

import com.yzytech.scx.common.job.annotation.BootEnableXxlJob;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 单体模块启动类
 *
 * @since 2022/2/18 13:52
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.yzytech.scx",
			excludeFilters = {
					@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
					@ComponentScan.Filter(type = FilterType.CUSTOM,
							classes = AutoConfigurationExcludeFilter.class),
					@ComponentScan.Filter(type = FilterType.REGEX,
							pattern = "com\\.yzytech\\.scx\\.ocpx\\.ScxOcpxEngineApplication") })
@EnableScheduling
@BootEnableXxlJob
public class ScxApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScxApplication.class, args);
	}

}
