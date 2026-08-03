package com.yzytech.scx.adx.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CatalogRequests {

	private CatalogRequests() {
	}

	public record SaveChannel(@NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$") String channelCode,
			@NotBlank @Size(max = 100) String channelName, @NotBlank @Size(max = 32) String adapterCode,
			@NotBlank @Pattern(regexp = "HMAC|NONE") String authType, @NotNull @DecimalMin("1") Integer keyVersion,
			@NotBlank @Size(max = 512) String uri, @Size(max = 255) String secretKey,
			@NotBlank @Size(max = 1000) String macroParams, @NotBlank @Size(max = 1000) String callbackHostAllowlist,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status) {
	}

	public record SaveAdvertiser(@NotBlank @Size(max = 100) String advertiserName,
			@NotBlank @Size(max = 32) String adapterCode, @NotBlank @Size(max = 512) String bidUrl,
			@Size(max = 255) String secretKey, @Size(max = 1000) String macroParams,
			@NotBlank @Size(max = 1000) String upstreamHostAllowlist, @NotBlank @Size(max = 100) String account,
			@Size(min = 8, max = 72) String password, @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status) {
	}

	public record Recharge(@NotNull @DecimalMin("0.01") BigDecimal amount) {
	}

	public record SaveAppLink(@NotBlank String advertiserId, @NotNull Integer linkPlatform,
			@NotBlank @Size(max = 100) String linkName, @Size(max = 100) String linkChannel,
			@NotBlank @Size(max = 1000) String linkUrl, @Size(max = 1000) String deeplink,
			@Size(max = 1000) String linkExt, @Size(max = 500) String linkRemark) {
	}

	public record SaveMonitor(@NotBlank String channelId, @NotBlank String advertiserId, @NotBlank String appLinkId,
			@NotBlank @Size(max = 100) String monitorName, @Size(max = 100) String bizId,
			@NotNull @DecimalMin("0") BigDecimal unitPrice, @Size(max = 500) String remark,
			@Size(max = 1500) String downstreamCallbackUrl, @NotBlank @Pattern(regexp = "OFF") String rtaMode,
			@NotBlank @Pattern(regexp = "OFF|AUDIT_ONLY") String billingMode) {
	}

	public record SaveDeduction(@NotBlank String channelId, @NotBlank String appLinkId,
			@NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal ratio) {
	}

	public record SaveClue(@NotNull Integer sourceType, @NotNull Integer clueStatus, @Size(max = 100) String clueName,
			@NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String cluePhone,
			@Size(max = 100) String clueIndex, @Size(max = 100) String sonTaskId, @Size(max = 64) String clueCityId,
			@Size(max = 64) String clueAreaId, @Size(max = 64) String clueBrandId,
			@Size(max = 64) String clueCarSeriesId, @Size(max = 64) String clueCarModelId,
			LocalDateTime firstCarRegistrationTime, @DecimalMin("0") BigDecimal carMileage,
			@Size(max = 500) String remark) {
	}

	public record BatchClueStatus(@NotNull Integer status, java.util.List<String> ids, String mode,
			Integer sourceStatus, LocalDate startDate, LocalDate endDate, String keyword, String cluePhone,
			Integer sourceType) {
	}

}
