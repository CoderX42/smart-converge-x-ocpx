package com.yzytech.scx.ocpx.configuration.credential;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 服务端合作方凭据字面量；Map key 对应管理库保存的 credentialConfigKey。 */
@Data
@Component
@ConfigurationProperties(prefix = "ocpx.partner-credentials")
public class PartnerCredentialProperties {

	private Map<String, String> advertisers = new HashMap<>();

	private Map<String, String> oppo = new HashMap<>();

	private Map<String, OppoUpload> oppoUpload = new HashMap<>();

	@Data
	public static class OppoUpload {

		private String salt;

		private String aesKey;

		private String ownerId;

		private String apiId;

		private String apiKey;

	}

}
