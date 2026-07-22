package com.yzytech.scx.delivery.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusChangeRequest(@NotBlank String status, @NotBlank String reason, Integer version) {
}
