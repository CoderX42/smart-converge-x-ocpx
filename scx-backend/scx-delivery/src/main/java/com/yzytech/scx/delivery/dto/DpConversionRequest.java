package com.yzytech.scx.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DpConversionRequest {
    @NotBlank
    private String linkType;

    @NotBlank
    private String sourceUrl;
}
