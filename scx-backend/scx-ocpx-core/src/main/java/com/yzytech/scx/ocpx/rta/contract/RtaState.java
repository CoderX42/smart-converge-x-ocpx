package com.yzytech.scx.ocpx.rta.contract;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** Redis-only state. Never expose encryptedPayload through management APIs. */
@Data
public class RtaState {

	private String requestId;

	private String relayRequestId;

	private String requestHash;

	private String mediaId;

	private String advertiserId;

	private RtaContract.Upstream upstream;

	private RtaContract.Media media;

	private List<RtaContract.Binding> bindings = new ArrayList<>();

	private List<String> matchedTaskIds = new ArrayList<>();

	private String status = "PENDING";

	private String deliveryStatus = "PENDING";

	private String reasonCode = "ACCEPTED";

	private String decisionHash = "";

	private String encryptedPayload;

	private long receivedAt;

	private long deadline;

	private long completedAt;

	private long expiresAt;

	private long deliveryDeadline;

	private int deliveryAttempts;

	private int upstreamAttempts;

}
