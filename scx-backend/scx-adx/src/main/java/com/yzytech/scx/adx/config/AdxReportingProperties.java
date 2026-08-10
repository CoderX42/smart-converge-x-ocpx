package com.yzytech.scx.adx.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "scx.adx.reporting")
public class AdxReportingProperties {

	@Min(1)
	@Max(31)
	private int defaultQueryDays = 7;

	@Min(1)
	@Max(366)
	private int maximumQueryDays = 90;

	@Min(1)
	@Max(731)
	private int maximumExportDays = 366;

	@Min(1)
	@Max(200)
	private int maximumPageSize = 200;

	@Min(1)
	@Max(10000)
	private int aggregationBatchSize = 500;

	@Min(1)
	@Max(100000)
	private int maximumExportRows = 100000;

	@Min(60)
	@Max(86400)
	private int exportTtlSeconds = 3600;

	@Min(1)
	@Max(300)
	private int dashboardCacheTtlSeconds = 45;

	private boolean aggregationWorkerEnabled;

	private boolean exportWorkerEnabled;

	public int getDefaultQueryDays() {
		return defaultQueryDays;
	}

	public void setDefaultQueryDays(int defaultQueryDays) {
		this.defaultQueryDays = defaultQueryDays;
	}

	public int getMaximumQueryDays() {
		return maximumQueryDays;
	}

	public void setMaximumQueryDays(int maximumQueryDays) {
		this.maximumQueryDays = maximumQueryDays;
	}

	public int getMaximumExportDays() {
		return maximumExportDays;
	}

	public void setMaximumExportDays(int maximumExportDays) {
		this.maximumExportDays = maximumExportDays;
	}

	public int getMaximumPageSize() {
		return maximumPageSize;
	}

	public void setMaximumPageSize(int maximumPageSize) {
		this.maximumPageSize = maximumPageSize;
	}

	public int getAggregationBatchSize() {
		return aggregationBatchSize;
	}

	public void setAggregationBatchSize(int aggregationBatchSize) {
		this.aggregationBatchSize = aggregationBatchSize;
	}

	public int getExportTtlSeconds() {
		return exportTtlSeconds;
	}

	public int getMaximumExportRows() {
		return maximumExportRows;
	}

	public void setMaximumExportRows(int maximumExportRows) {
		this.maximumExportRows = maximumExportRows;
	}

	public void setExportTtlSeconds(int exportTtlSeconds) {
		this.exportTtlSeconds = exportTtlSeconds;
	}

	public int getDashboardCacheTtlSeconds() {
		return dashboardCacheTtlSeconds;
	}

	public void setDashboardCacheTtlSeconds(int dashboardCacheTtlSeconds) {
		this.dashboardCacheTtlSeconds = dashboardCacheTtlSeconds;
	}

	public boolean isAggregationWorkerEnabled() {
		return aggregationWorkerEnabled;
	}

	public void setAggregationWorkerEnabled(boolean aggregationWorkerEnabled) {
		this.aggregationWorkerEnabled = aggregationWorkerEnabled;
	}

	public boolean isExportWorkerEnabled() {
		return exportWorkerEnabled;
	}

	public void setExportWorkerEnabled(boolean exportWorkerEnabled) {
		this.exportWorkerEnabled = exportWorkerEnabled;
	}

}
