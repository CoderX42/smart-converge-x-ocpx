package com.yzytech.scx.ocpx.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WireAuditRecord {

	private String id;
	private String traceId;
	private String flowLogId;
	private String stageCode;
	private String direction;
	private String trafficClass;
	private String protocol;
	private String requestMethod;
	private String requestPath;
	private boolean wireSent;
	private String resultStatus;
	private Integer partnerHttpStatus;
	private String errorCode;
	private String requestId;
	private String advertiserId;
	private String promotionLinkId;
	private String mediaId;
	private String monitorTaskId;
	private long requestBytes;
	private long responseBytes;
	private String requestSha256;
	private String responseSha256;
	private String rawState;
	private String keyId;
	private int cryptoVersion;
	private String captureSource;
	private LocalDateTime capturedAt;
	private LocalDateTime finishedAt;
	private LocalDateTime rawExpiresAt;
	private LocalDateTime metadataExpiresAt;
	private String payloadAad;
	private byte[] payloadNonce;
	private byte[] payloadCiphertext;

}
