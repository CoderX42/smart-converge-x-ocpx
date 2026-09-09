package com.yzytech.scx.ocpx.admin.rta;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/** Server-only media signing credentials. Never returned by management APIs. */
@Data
@Component
@ConfigurationProperties(prefix = "app.rta-relay")
public class RtaTestProperties {

	private Map<String, String> credentials = new HashMap<>();

}
