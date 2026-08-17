package com.yzytech.scx.ocpx.feedback.delivery.weifeng;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 微风互动点击监测协议的服务端配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "ocpx.weifeng")
public class WeifengProperties {

	/** 微风互动商务确认的点击监测域名，不包含协议和路径。 */
	private Set<String> allowedHosts = new HashSet<>();

	/** SCX 媒体主键到微风互动 did 的映射。 */
	private Map<String, String> mediaIds = new HashMap<>();

}
