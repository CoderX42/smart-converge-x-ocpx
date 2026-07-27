package com.yzytech.scx.reporting.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.Data;

/**
 * Operational click fields available only to the controlled troubleshooting permission.
 *
 * <p>Device identifiers and IP addresses are deliberately represented as presence flags,
 * never as their original values or full hashes.</p>
 */
@Data
public class ClickSensitiveDetail {

    private String rawEventId;
    private String clickId;
    private String deviceModel;
    private String campaignId;
    private String creativeId;
    private Boolean oaidProtected;
    private Boolean oaidMd5Protected;
    private Boolean ipProtected;
    private List<String> attributeKeys;

    @JsonIgnore
    private String attributeKeysJson;
}
