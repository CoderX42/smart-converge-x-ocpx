
package com.yzytech.scx.common.datasource;

import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.yzytech.scx.common.datasource.config.BootDataSourceProperties;
import com.yzytech.scx.common.datasource.config.JdbcDynamicDataSourceProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@EnableConfigurationProperties
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class BootDataSourceAutoConfiguration {

	@Bean
	@Lazy
	public BootDataSourceProperties druidDataSourceProperties() {
		return new BootDataSourceProperties();
	}

	@Bean
	public DynamicDataSourceProvider dynamicDataSourceProvider(DefaultDataSourceCreator defaultDataSourceCreator) {
		return new JdbcDynamicDataSourceProvider(defaultDataSourceCreator, druidDataSourceProperties());
	}

}
