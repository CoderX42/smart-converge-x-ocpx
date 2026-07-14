

package com.yzytech.scx.common.dubbo.config;

import com.yzytech.scx.common.core.factory.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @description dubbo配置
 * @date 2024/11/25
 */
@PropertySource(value = "classpath:dubbo-config.yml", factory = YamlPropertySourceFactory.class)
@ConditionalOnProperty(prefix = "boot", value = "cloud.enable", matchIfMissing = true)
@Configuration(proxyBeanMethods = false)
public class BootDubboConfiguration {

}
