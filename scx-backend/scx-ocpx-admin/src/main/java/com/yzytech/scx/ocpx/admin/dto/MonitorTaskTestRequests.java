package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public final class MonitorTaskTestRequests {

	private MonitorTaskTestRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Generate {

		@NotBlank
		@Pattern(regexp = "ANDROID|IOS")
		private String os;

		@Size(max = 24)
		private List<@Pattern(regexp = "[a-z][a-z0-9_]{0,31}") String> parameters;

		/** 编辑后的参数只用于预览校验，不调用 Engine。未提供时生成随机值。 */
		@Size(max = 24)
		private Map<String, String> parameterValues;

		/**
		 * 真实媒体 Feedback 明细主键。测试只能复用媒体实际提供的 callback_url，不能由浏览器
		 * 任意指定目标地址。
		 */
		private Long feedbackLogId;

		public Generate(String os, List<String> parameters) {
			this(os, parameters, null, null);
		}

		public Generate(String os, List<String> parameters, Map<String, String> parameterValues) {
			this(os, parameters, parameterValues, null);
		}

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Execute {

		@NotBlank
		@Size(max = 128)
		private String requestId;

		@NotBlank
		@Pattern(regexp = "ANDROID|IOS")
		private String os;

		@NotBlank
		@Pattern(regexp = "[0-9a-f]{32}")
		private String callbackToken;

		private Long feedbackLogId;

		@NotNull
		@Size(max = 24)
		private Map<String, String> parameters;

		public Execute(String requestId, String os, String callbackToken, Map<String, String> parameters) {
			this(requestId, os, callbackToken, null, parameters);
		}

	}

}
