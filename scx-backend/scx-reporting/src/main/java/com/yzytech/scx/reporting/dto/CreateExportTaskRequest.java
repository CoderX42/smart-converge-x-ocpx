package com.yzytech.scx.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateExportTaskRequest {
    @NotBlank
    private String mediaId;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
    private String exportType;
}
