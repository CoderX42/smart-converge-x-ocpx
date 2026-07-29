package com.yzytech.scx.inventory.adapter;

import com.yzytech.scx.inventory.dto.*;

/**
 * Unified media adapter interface — anti-corruption layer. All external media differences
 * are isolated behind this interface. Core domains MUST NOT reference external media
 * enums directly.
 */
public interface MediaAdapter {

	String adapterType();

	/** Query adapter capabilities (billing modes, events, formats, etc.) */
	CapabilityView getCapabilities();

	/** Sync placements from external media (incremental cursor) */
	java.util.List<com.yzytech.scx.inventory.entity.Placement> syncPlacements(String mediaAccountId, String cursor);

	/** Idempotent campaign publish */
	PublishResult publishCampaign(PublishRequest request);

	/** Change campaign status on external media */
	PublishResult changeStatus(String externalCampaignId, String newStatus, String idempotencyKey);

	/** Query current status from external media */
	StatusQueryResult queryStatus(String externalCampaignId);

	/** Pull performance report with incremental cursor */
	ReportPullResult pullReport(String mediaAccountId, String startDate, String endDate, String cursor);

	/** Pull billing/consumption data */
	ReportPullResult pullBilling(String mediaAccountId, String startDate, String endDate, String cursor);

	/** Health check */
	boolean isHealthy();

}
