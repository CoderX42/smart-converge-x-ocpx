package com.yzytech.scx.ocpx.admin.dto;

import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class PromotionLinkViews {

	private PromotionLinkViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;

		private String advertiserId;

		private String advertiserName;

		private String linkName;

		private Integer platform;

		private String linkChannel;

		private String linkUrl;

		private String deeplink;

		private String linkExt;

		private String remark;

		private LocalDateTime createTime;

		private LocalDateTime updateTime;

	}

	@Data
	@NoArgsConstructor
	public static class AdvertiserOption {

		private String id;

		private String name;

		/** 协议用于前端在推广链接表单中应用协议级校验。 */
		private OcpxUpstreamProtocol protocol;

		public AdvertiserOption(String id, String name) {
			this(id, name, null);
		}

		public AdvertiserOption(String id, String name, OcpxUpstreamProtocol protocol) {
			this.id = id;
			this.name = name;
			this.protocol = protocol;
		}

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Option {

		private String id;

		private String name;

	}

}
