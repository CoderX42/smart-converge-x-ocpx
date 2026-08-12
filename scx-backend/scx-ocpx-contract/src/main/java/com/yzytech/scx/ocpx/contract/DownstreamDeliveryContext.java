package com.yzytech.scx.ocpx.contract;

/** In-memory-only context used by a downstream adapter to build one callback request. */
public record DownstreamDeliveryContext(OcpxPartnerContext partner, CallbackReqV2 callback, String account,
		String secret) {
}
