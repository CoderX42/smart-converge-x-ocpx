package com.yzytech.scx.tracking.dto;

import java.time.LocalDateTime;

public record ChannelCredentialIssueResponse(String channelClientId, String clientId, String keyVersion, String secret,
		LocalDateTime issuedAt, LocalDateTime previousKeyValidUntil) {
}
