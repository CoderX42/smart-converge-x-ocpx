package com.yzytech.scx.ocpx.contract;

/**
 * One active monitor task joined with its advertiser, promotion link and media.
 * This context is rebuilt from the four business tables for every request and is
 * never persisted as a route or snapshot.
 */
public record OcpxPartnerContext(String advertiserId, String advertiserCode, String promotionLinkId,
		String mediaId, String mediaCode, String monitorTaskId, String monitorTaskCode,
		String upstreamAdapterCode, String advertiserCredentialConfigKey, long advertiserCredentialVersion,
		String bidUrl, String downstreamAdapterCode, String mediaAuthType, String mediaCredentialConfigKey,
		long mediaCredentialVersion, String downstreamCallbackUrl, String promotionUrlTemplate,
		long configVersion) {
}
