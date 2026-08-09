package com.yzytech.scx.common.log.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Process-local, bounded and sanitized log view for the administrator debugger. Entries
 * are intentionally not restored after restart.
 */
public final class BoundedSanitizedLogAppender extends AppenderBase<ILoggingEvent> {

	private static final int MAX_ENTRIES = 5_000;

	private static final long RETENTION_MILLIS = 2 * 60 * 60 * 1_000L;

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
		.withZone(ZoneId.systemDefault());

	private static final Pattern SENSITIVE = Pattern.compile(
			"(?i)([\"']?(?:authorization|cookie|password|passwd|secret|token|signature|x-sign|appsecret|access[_-]?key|"
					+ "hmac[_-]?key|aes[_-]?key|private[_-]?key|phone|mobile|ip|oaid|idfa|imei|caid|android[_-]?id|"
					+ "id[_-]?card|identity[_-]?number|real[_-]?name)"
					+ "[\"']?)(\\s*[:=]\\s*|\\s+)(Bearer\\s+[^,;\\s}]+|[\"'][^\"']*[\"']|[^,;\\s}]+)");

	private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[^,;\\s}]+");

	private static final Pattern CONNECTION_STRING = Pattern
		.compile("(?i)\\b(?:jdbc:[^\\s,;]+|(?:mongodb|mysql|postgres(?:ql)?|redis)://[^\\s,;]+)");

	private static final Deque<Entry> ENTRIES = new ArrayDeque<>(MAX_ENTRIES);

	private static final AtomicLong SEQUENCE = new AtomicLong();

	@Override
	protected void append(ILoggingEvent event) {
		record(event.getTimeStamp(), event.getLevel().levelStr, event.getLoggerName(), event.getFormattedMessage(),
				event.getMDCPropertyMap().get("debugExecutionId"));
	}

	static void record(long timestamp, String level, String logger, String rawMessage, String debugExecutionId) {
		long now = System.currentTimeMillis();
		String message = sanitizeText(rawMessage);
		if (debugExecutionId != null && !debugExecutionId.isBlank()) {
			message = "[debugExecutionId=" + sanitizeText(debugExecutionId) + "] " + message;
		}
		synchronized (ENTRIES) {
			purge(now);
			while (ENTRIES.size() >= MAX_ENTRIES) {
				ENTRIES.removeFirst();
			}
			ENTRIES.addLast(new Entry(SEQUENCE.incrementAndGet(), timestamp, level, logger, truncate(message, 4_000)));
		}
	}

	public static QueryResult query(String level, String logger, String keyword, long afterSequence) {
		long now = System.currentTimeMillis();
		List<LogRecord> records = new ArrayList<>();
		long nextCursor = afterSequence;
		boolean truncated = false;
		synchronized (ENTRIES) {
			purge(now);
			for (Entry entry : ENTRIES) {
				if (entry.sequence() <= afterSequence) {
					continue;
				}
				nextCursor = Math.max(nextCursor, entry.sequence());
				if (hasText(level) && !entry.level().equalsIgnoreCase(level)
						|| hasText(logger)
								&& !entry.logger().toLowerCase(Locale.ROOT).contains(logger.toLowerCase(Locale.ROOT))
						|| hasText(keyword) && !entry.message()
							.toLowerCase(Locale.ROOT)
							.contains(keyword.toLowerCase(Locale.ROOT))) {
					continue;
				}
				if (records.size() == 500) {
					truncated = true;
					break;
				}
				records.add(new LogRecord(entry.sequence(), TIMESTAMP.format(Instant.ofEpochMilli(entry.timestamp())),
						entry.level(), entry.logger(), entry.message()));
			}
		}
		return new QueryResult(List.copyOf(records), nextCursor, truncated);
	}

	private static void purge(long now) {
		while (!ENTRIES.isEmpty() && ENTRIES.peekFirst().timestamp() < now - RETENTION_MILLIS) {
			ENTRIES.removeFirst();
		}
	}

	public static String sanitizeText(String value) {
		if (value == null) {
			return "";
		}
		String singleLine = value.replaceAll("[\\r\\n]", " ");
		String sanitized = SENSITIVE.matcher(singleLine).replaceAll("$1$2[REDACTED]");
		sanitized = BEARER.matcher(sanitized).replaceAll("Bearer [REDACTED]");
		return CONNECTION_STRING.matcher(sanitized).replaceAll("[REDACTED_CONNECTION_STRING]");
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}

	private record Entry(long sequence, long timestamp, String level, String logger, String message) {
	}

	public record LogRecord(long sequence, String timestamp, String level, String logger, String message) {
	}

	public record QueryResult(List<LogRecord> records, long nextCursor, boolean truncated) {
	}

}
