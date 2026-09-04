package com.yzytech.scx.ocpx.rta.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;



/** SCX 内部统一的 RTA 请求。原始设备值只在当前请求生命周期内存在。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class RtaDecisionRequest {

	@NotBlank
	@Size(min = 1, max = 128)
	private String requestId;

	@NotBlank
	@Size(max = 32)
	private String advertiserId;

	@NotBlank
	@Size(max = 32)
	private String promotionLinkId;

	@NotBlank
	@Size(max = 32)
	private String mediaId;

	@NotBlank
	@Size(max = 32)
	private String monitorTaskId;

	@Valid
	private Device device = new Device();

	@Valid
	private Context context = new Context();

	@Size(max = 16)
	private Map<@Size(max = 64) String, @Size(max = 256) String> attributes;

	@Data
	@JsonIgnoreProperties(ignoreUnknown = false)
	public static class Device {

		@Size(max = 256)
		private String oaid;

		@Size(max = 64)
		private String oaidMd5;

		@Size(max = 256)
		private String imei;

		@Size(max = 64)
		private String imeiMd5;

		@Size(max = 256)
		private String idfa;

		@Size(max = 64)
		private String idfaMd5;

		@Size(max = 256)
		private String caid;

		@Size(max = 64)
		private String caidVersion;

		@Size(max = 256)
		private String preCaid;

		@Size(max = 64)
		private String preCaidVersion;

	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = false)
	public static class Context {

		@Size(max = 64)
		private String ip;

		@Size(max = 128)
		private String ipv6;

		@Size(max = 32)
		private String os;

		@Size(max = 64)
		private String osv;

		@Size(max = 128)
		private String model;

		@Size(max = 1024)
		private String ua;

	}

}
