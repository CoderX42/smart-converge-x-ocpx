package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RtaAdminViews {

	private RtaAdminViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Profile {

		private String id;

		private String code;

		private String name;

		private String adapterCode;

		private String status;

		private String configJson;

		private Integer version;

		private LocalDateTime createTime;

		private LocalDateTime updateTime;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ProfilePage {

		private List<Profile> records;

		private long current;

		private long size;

		private long total;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Binding {

		private String id;

		private String monitorTaskId;

		private String profileId;

		private String profileName;

		private String externalTaskId;

		private String externalProductId;

		private Boolean enabled;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TaskConfig {

		private String monitorTaskId;

		private Boolean enabled;

		private String profileId;

		private List<Binding> bindings;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Decision {

		private String id;

		private String requestId;

		private String advertiserId;

		private String mediaId;

		private String promotionLinkId;

		private String monitorTaskId;

		private String profileId;

		private String profileName;

		private String decision;

		private String outcomeClass;

		private String reasonCode;

		private Long latencyMs;

		private LocalDateTime receivedAt;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DecisionPage {

		private List<Decision> records;

		private long current;

		private long size;

		private long total;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Metric {

		private LocalDateTime bucketStart;

		private String advertiserId;

		private String promotionLinkId;

		private String mediaId;

		private String monitorTaskId;

		private String profileId;

		private String profileName;

		private Long requestCount;

		private Long allowCount;

		private Long denyCount;

		private Long timeoutCount;

		private Long errorCount;

		private BigDecimal allowRate;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MetricPage {

		private List<Metric> records;

		private String dataAsOf;

	}

}
