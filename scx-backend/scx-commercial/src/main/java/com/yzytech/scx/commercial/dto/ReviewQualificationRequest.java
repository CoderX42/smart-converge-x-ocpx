package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record ReviewQualificationRequest(@NotBlank String decision, String comment, LocalDateTime expiredAt) {
}
