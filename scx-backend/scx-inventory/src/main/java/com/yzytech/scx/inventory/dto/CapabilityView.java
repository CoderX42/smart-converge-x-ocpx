package com.yzytech.scx.inventory.dto;

import java.util.List;

public record CapabilityView(String adapterType, List<String> billingModes, List<String> targetEvents,
		List<String> creativeFormats, boolean supportsOcpc, boolean supportsStatusSync, boolean supportsReportPull) {
}
