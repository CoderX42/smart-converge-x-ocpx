package com.yzytech.scx.reporting.dto;

import lombok.Data;

/** Sensitive click attributes are masked at SQL projection time and never returned raw. */
@Data
public class ClickSensitiveDetail {
    private String rawEventId;
    private String clickIdMasked;
    private String oaidMasked;
    private String oaidMd5Masked;
    private String ipMasked;
    private String deviceModel;
    private String campaignIdMasked;
    private String creativeIdMasked;
}
