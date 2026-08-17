package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class CreativeViews {

	private CreativeViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private String id;
		private String advertiserId;
		private String advertiserName;
		private String creativeName;
		private Integer creativeType;
		private String assetUrl;
		private String sourceMaterialId;
		private String fileName;
		private String mimeType;
		private Long fileSize;
		private Integer width;
		private Integer height;
		private String checksumSha256;
		private Integer status;
		private String remark;
		private List<LinkOption> promotionLinks;
		private LocalDateTime createTime;
		private LocalDateTime updateTime;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class LinkOption {
		private String id;
		private String name;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Asset {
		private String sourceMaterialId;
		private String fileName;
		private String assetUrl;
		private Long fileSize;
		private LocalDateTime createTime;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class UploadedAsset {
		private String sourceMaterialId;
		private String fileName;
		private String assetUrl;
		private String mimeType;
		private Long fileSize;
		private Integer width;
		private Integer height;
		private String checksumSha256;
		private List<String> duplicateCreativeIds;
	}

}
