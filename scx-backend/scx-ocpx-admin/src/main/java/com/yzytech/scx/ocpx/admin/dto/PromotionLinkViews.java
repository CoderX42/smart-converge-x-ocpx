package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class PromotionLinkViews {

	private PromotionLinkViews() {
	}

	public record Item(String id, String advertiserId, String advertiserName, String linkCode, String linkName,
			String packageName, String platform, String linkType, String linkChannel, String linkUrlTemplate,
			String deeplinkUrl, String downloadUrl, List<String> macroSet, boolean redirectEnabled, String remark,
			String status, long configVersion, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record AdvertiserOption(String id, String code, String name) {
	}

	public record Mutation(String id, String status, long configVersion) {
	}

	public record ValidationReport(String status, boolean networkCalled, String normalizedMaskedUrl, String host,
			String fingerprint, List<String> macroSet, List<String> issues) {
	}

}
