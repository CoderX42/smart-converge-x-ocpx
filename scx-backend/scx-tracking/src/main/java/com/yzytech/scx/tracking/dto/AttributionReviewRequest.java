package com.yzytech.scx.tracking.dto;

import jakarta.validation.constraints.NotBlank;

/** Manual settlement decision for one immutable attribution revision. */
public record AttributionReviewRequest(@NotBlank String status, String reason) {
}
