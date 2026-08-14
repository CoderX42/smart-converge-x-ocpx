package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedReportQuery {

	private LocalDate startDate;

	private LocalDate endDate;

	private String groupBy;

	private String advertiserId;

	private String promotionLinkId;

	private String mediaId;

	private String monitorTaskId;

	private long current;

	private long size;

}
