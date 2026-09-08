package com.yzytech.scx.ocpx.rta.relay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "app.rta-relay")
public class RelayProperties {

	private boolean enabled;

	private String callbackBaseUrl = "";

	private String payloadKey = "";

	private Map<String, String> credentials = new HashMap<>();

	private Set<String> allowedHosts = new HashSet<>();

}
