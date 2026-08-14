package com.yzytech.scx.ocpx.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummaryReportQuery {

	private Integer dateType;

	private String mediaId;

	private String monitorTaskId;

	private String advertiserId;

	private String promotionLinkId;

	private String startTime;

	private String endTime;

	private String asc;

	private String desc;

	private Long current;

	private Long size;

}
