package com.yzytech.scx.ocpx.contract;

/** In-memory-only context used by an upstream adapter to build one feedback request. */
public record UpstreamDeliveryContext(OcpxPartnerContext partner, FeedbackReqV2 feedback,
		String upstreamEventCode, String callbackUrl, String account, String secret) {
}
