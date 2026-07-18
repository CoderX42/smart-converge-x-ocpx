package com.yzytech.scx.reporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_export_task")
public class ExportTask {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String requesterUserId;
    private String mediaId;
    private String exportType;
    private LocalDate reportStartDate;
    private LocalDate reportEndDate;
    private String taskScopeSnapshot;
    private String querySnapshotHash;
    private String status;
    private Integer rowCount;
    private String artifactName;
    @JsonIgnore
    private byte[] artifactContent;
    private String artifactSha256;
    private LocalDateTime expiresAt;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
