
package com.yzytech.scx.common.datasource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.datasource.dynamic.datasource.master")
public class BootDataSourceProperties {

	private String url;

	private String username;

	private String password;

	private String driverClassName;

}
