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
		 * 真实媒体模式使用的 Feedback 明细主键。模拟媒体模式不允许指定。
		 */
		private Long feedbackLogId;

		@Pattern(regexp = "REAL_MEDIA|SIMULATED_MEDIA")
		private String mediaCallbackMode;

		public Generate(String os, List<String> parameters) {
			this(os, parameters, null, null, null);
		}

		public Generate(String os, List<String> parameters, Map<String, String> parameterValues) {
			this(os, parameters, parameterValues, null, null);
		}

		public Generate(String os, List<String> parameters, String mediaCallbackMode) {
			this(os, parameters, null, null, mediaCallbackMode);
		}

		public Generate(String os, List<String> parameters, Map<String, String> parameterValues,
				String mediaCallbackMode) {
			this(os, parameters, parameterValues, null, mediaCallbackMode);
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

		@Pattern(regexp = "REAL_MEDIA|SIMULATED_MEDIA")
		private String mediaCallbackMode;

		@NotNull
		@Size(max = 24)
		private Map<String, String> parameters;

		public Execute(String requestId, String os, String callbackToken, Map<String, String> parameters) {
			this(requestId, os, callbackToken, null, null, parameters);
		}

		public Execute(String requestId, String os, String callbackToken, String mediaCallbackMode,
				Map<String, String> parameters) {
			this(requestId, os, callbackToken, null, mediaCallbackMode, parameters);
		}

	}

}
