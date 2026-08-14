package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;

public final class MediaViews {

	private MediaViews() {
	}

	public record Item(String id, String mediaName, String credentialConfigKey, String uri, String macroParams,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Option(String id, String name) {
	}

}
