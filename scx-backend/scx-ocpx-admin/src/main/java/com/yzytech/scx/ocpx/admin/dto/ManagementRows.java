package com.yzytech.scx.ocpx.admin.dto;

import lombok.Data;

public final class ManagementRows {

	private ManagementRows() {
	}

	@Data
	public static class AdvertiserUsername {

		private String advertiserId;

		private String username;

	}

	@Data
	public static class NamedOption {

		private String id;

		private String name;

	}

	@Data
	public static class RouteKey {

		private String promotionLinkId;

		private String mediaId;

	}

}
