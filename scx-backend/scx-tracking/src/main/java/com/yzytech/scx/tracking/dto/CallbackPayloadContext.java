package com.yzytech.scx.tracking.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** Non-sensitive attribution facts needed to build a media callback payload. */
@Data
public class CallbackPayloadContext {

	private String taskCode;

	private String linkId;

	private Integer linkConfigVersion;

	private String internalEvent;

	private LocalDateTime occurredAt;

}
