package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class AccessScopeViews {

	private AccessScopeViews() {
	}

	public record Scope(String id, String scopeType, String scopeId, String scopeName, String permissionLevel,
			LocalDateTime validFrom, LocalDateTime validTo, String status) {
	}

	public record UserScopes(String userId, long version, List<Scope> scopes) {
	}

	public record Option(String scopeType, String id, String name, String status) {
	}

	public record Options(boolean allManageable, List<Option> advertisers, List<Option> channels,
			List<Option> mediaAccounts) {
	}

	public record Impact(String userId, long version, long advertiserCount, long channelCount, long mediaAccountCount,
			long appLinkCount, long monitorCount, long publishedMonitorCount) {
	}

}
