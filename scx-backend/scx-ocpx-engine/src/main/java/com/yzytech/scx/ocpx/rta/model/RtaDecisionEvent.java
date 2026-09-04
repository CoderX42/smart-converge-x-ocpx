package com.yzytech.scx.ocpx.rta.model;

import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;



/** Redis Stream 中的脱敏 RTA 事实事件，不包含原始设备标识。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RtaDecisionEvent {

	private String requestId;

	private String requestHash;

	private String advertiserId;

	private String promotionLinkId;

	private String mediaId;

	private String monitorTaskId;

	private String profileId;

	private String adapterCode;

	private RtaDecision decision;

	private String outcomeClass;

	private String reasonCode;

	private long latencyMs;

	private List<String> matchedTaskIds;

	private LocalDateTime receivedAt;

	private LocalDateTime completedAt;

}
