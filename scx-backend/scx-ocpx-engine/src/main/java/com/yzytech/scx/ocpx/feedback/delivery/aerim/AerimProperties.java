package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** AERIM 上游出站地址白名单。 */
@Data
@Component
@ConfigurationProperties(prefix = "ocpx.aerim")
public class AerimProperties {

	private Set<String> allowedHosts = new HashSet<>();

}
