package com.yzytech.scx.ocpx.rta.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;



/** Engine 侧 RTA 开关与 Redis Stream 参数。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rta")
public class RtaProperties {

	private boolean enabled;

	private boolean mockEnabled;

	private long admissionTtlSeconds = 300;

	private String streamKey = "r:ocpx:rta:decision-fact";

	private String consumerGroup = "scx-ocpx-rta-facts";

	private int batchSize = 100;

	private int maxRetries = 3;

	private long claimIdleSeconds = 60;

}
