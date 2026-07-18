package com.yzytech.scx.reporting.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class ReportTrendPoint {
    private LocalDate reportDate;
    private Long impressionCount;
    private Long uniqueClickCount;
    private Long eligibleClickCount;
    private Long attributedActivateCount;
    private Long attributedRegisterCount;
    private Long attributedPayCount;
    private Long attributedOrderCount;
    private Long nextDayReturnCount;
}
