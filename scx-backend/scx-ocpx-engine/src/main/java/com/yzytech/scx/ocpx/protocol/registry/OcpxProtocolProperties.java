package com.yzytech.scx.ocpx.protocol.registry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 服务端配置的广告主协议主键注册表。 */
@Data
@Component
@ConfigurationProperties(prefix = "ocpx.protocols")
public class OcpxProtocolProperties {

	private Map<String, AdvertiserProtocolRegistry.Protocol> advertisers = new HashMap<>();

}
