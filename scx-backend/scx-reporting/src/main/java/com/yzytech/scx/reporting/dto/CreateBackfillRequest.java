package com.yzytech.scx.reporting.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateBackfillRequest {

	private String mediaId;

	private LocalDate startDate;

	private LocalDate endDate;

	private boolean dryRun = true;

}
