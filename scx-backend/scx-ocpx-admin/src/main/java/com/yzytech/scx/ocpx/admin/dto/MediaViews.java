package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class MediaViews {

	private MediaViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;

		private String mediaName;

		private String credentialConfigKey;

		private String uri;

		private String macroParams;

		private LocalDateTime createTime;

		private LocalDateTime updateTime;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Option {

		private String id;

		private String name;

	}

}
