package com.yzytech.scx.reporting.dto;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class EventDetailView {
    private String rawEventId;
    private String eventId;
    private String source;
    private String taskId;
    private String taskCode;
    private String eventType;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private String deviceKeyMasked;
    private String ingestionStatus;
    private String dedupStatus;
    private String attributionStatus;
    private String reasonCode;
    private Integer revision;
}
