package com.yzytech.scx.tracking.dto;

public record RtaDecisionResponse(String requestId, String decision, String reasonCode, boolean duplicateDelivery) {
}
