package com.yzytech.scx.ocpx.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties("app.callback-crypto")
public class CallbackCryptoProperties {

	private String activeKeyId;

	private Map<String, String> keys = new LinkedHashMap<>();

	private List<String> legacyOrder = new ArrayList<>();

}
