package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxUnifiedReportSqlMapper {

	Long countSummary(@Param("q") com.yzytech.scx.ocpx.admin.dto.SummaryReportQuery q,
			@Param("startHour") int startHour, @Param("endHour") int endHour);

	record SelectSummaryRow(Object linkRadio, String reportTime, String mId, String mediaName, String tId,
			String monitorName, String aId, String advertiserName, String lId, String linkName, long callbackCount,
			long deductionPassedCount, long deductionDeductedCount, long uniShowSmt, long uniClkSmt, Double clickRate,
			java.math.BigDecimal basePrice, long activate, long evoke, long login, long register, long payment,
			long customerAcquisition, long nextStay, long visits, long authentication, long placeOrder,
			long finishTheOrder, long firstActivation, long firstOrder, long download, long activity, long addiction,
			java.math.BigDecimal currencyAmount) {
	}

	java.util.List<SelectSummaryRow> selectSummary(@Param("q") com.yzytech.scx.ocpx.admin.dto.SummaryReportQuery q,
			@Param("startHour") int startHour, @Param("endHour") int endHour, @Param("paged") boolean paged,
			@Param("limit") int limit, @Param("offset") int offset);

	java.time.LocalDateTime deductionCoverageStart(@Param("q") com.yzytech.scx.ocpx.admin.dto.SummaryReportQuery q);

	java.util.List<com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews.Option> selectAdvertiserOptions(
			@Param("advertiserId") String advertiserId);

	java.util.List<com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews.Option> selectLinkOptions(
			@Param("advertiserId") String advertiserId);

	java.util.List<com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews.Option> selectMediaOptions(
			@Param("advertiserId") String advertiserId);

	java.util.List<com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews.Option> selectTaskOptions(
			@Param("advertiserId") String advertiserId, @Param("mediaId") String mediaId);

	record AccountActivity(java.math.BigDecimal todaySpend, long rejectedClickCount, long rejectedConversionCount) {
	}

	AccountActivity accountActivity(@Param("advertiserId") String advertiserId, @Param("start") int start,
			@Param("end") int end);

	java.time.LocalDateTime aggregateWatermark(@Param("q") com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery q,
			@Param("start") int start, @Param("end") int end);

	Long countAdvertiser(@Param("id") String id);

	Long countMedia(@Param("id") String id);

	Long countLink(@Param("q") com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery q);

	Long countTask(@Param("q") com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery q);

	record Aggregate(String dimensionKey, String dimensionName, long show, long uniqueShow, long click,
			long uniqueClick, long targetConversion, long callback, long deductionPassed, long deductionDeducted,
			long activate, long evoke, long login, long register, long payment, long customerAcquisition, long nextStay,
			long visits, long authentication, long placeOrder, long finishTheOrder, long firstActivation,
			long firstOrder, long download, long activity, long addiction, long rejectedClick,
			java.math.BigDecimal spendAmount, java.math.BigDecimal currencyAmount) {
		public java.util.Map<String, Long> counts() {
			return java.util.Map.ofEntries(java.util.Map.entry("SHOW", show),
					java.util.Map.entry("UNIQUE_SHOW", uniqueShow), java.util.Map.entry("CLICK", click),
					java.util.Map.entry("UNIQUE_CLICK", uniqueClick),
					java.util.Map.entry("TARGET_CONVERSION", targetConversion),
					java.util.Map.entry("CALLBACK", callback), java.util.Map.entry("DEDUCTION_PASSED", deductionPassed),
					java.util.Map.entry("DEDUCTION_DEDUCTED", deductionDeducted),
					java.util.Map.entry("ACTIVATE", activate), java.util.Map.entry("EVOKE", evoke),
					java.util.Map.entry("LOGIN", login), java.util.Map.entry("REGISTER", register),
					java.util.Map.entry("PAYMENT", payment),
					java.util.Map.entry("CUSTOMER_ACQUISITION", customerAcquisition),
					java.util.Map.entry("NEXT_STAY", nextStay), java.util.Map.entry("VISITS", visits),
					java.util.Map.entry("AUTHENTICATION", authentication),
					java.util.Map.entry("PLACE_ORDER", placeOrder),
					java.util.Map.entry("FINISH_THE_ORDER", finishTheOrder),
					java.util.Map.entry("FIRST_ACTIVATION", firstActivation),
					java.util.Map.entry("FIRST_ORDER", firstOrder), java.util.Map.entry("DOWNLOAD", download),
					java.util.Map.entry("ACTIVITY", activity), java.util.Map.entry("ADDICTION", addiction),
					java.util.Map.entry("REJECTED_CLICK", rejectedClick));
		}
	}

	java.util.List<Aggregate> aggregate(@Param("q") com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery q,
			@Param("start") int start, @Param("end") int end);

}
