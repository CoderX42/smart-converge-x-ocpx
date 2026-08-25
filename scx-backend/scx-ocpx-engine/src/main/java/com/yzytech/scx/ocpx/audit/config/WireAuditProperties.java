package com.yzytech.scx.ocpx.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("ocpx.audit")
public class WireAuditProperties {

	private boolean enabled;

	private String walPath = "./data/ocpx-audit-wal";

	private long walMaxBytes = 20L * 1024 * 1024 * 1024;

	private long segmentMaxBytes = 256L * 1024 * 1024;

	private long flushIntervalMillis = 5L;

	private int rawRetentionDays = 30;

	private int metadataRetentionDays = 180;

}
