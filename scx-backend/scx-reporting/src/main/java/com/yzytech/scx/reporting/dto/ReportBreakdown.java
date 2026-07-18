package com.yzytech.scx.reporting.dto;

import lombok.Data;

/**
 * A report range rolled up from the immutable daily snapshot grain.
 *
 * <p>The identifier, rather than the display name, is the grouping key so a
 * renamed product, link, or task does not merge historical figures.</p>
 */
@Data
public class ReportBreakdown {
    private String dimensionId;
    private String dimensionName;
    private Long impressionCount;
    private Long uniqueClickCount;
    private Long eligibleClickCount;
    private Long deduplicatedClickCount;
    private Long attributedActivateCount;
    private Long attributedRegisterCount;
    private Long attributedPayCount;
    private Long attributedOrderCount;
    private Long nextDayReturnCount;
    private Long retention3dCount;
    private Long retention7dCount;
    private Long firstActivateCount;
    private Long firstOrderCount;
    private Long reactivateCount;
    private Long customCount;
    private Long creditCount;
}
