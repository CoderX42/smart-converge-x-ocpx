package com.yzytech.scx.tracking.dto;

import java.time.LocalDateTime;

public record ChannelCredentialView(String keyVersion, String status, LocalDateTime validFrom, LocalDateTime validUntil,
		LocalDateTime createTime, boolean current) {
}
