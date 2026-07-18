package com.yzytech.scx.tracking.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Keeps the public event API interoperable with both the documented ISO-8601 timestamp
 * and the platform's historic space-separated date format.
 */
public class EventOccurredAtDeserializer extends StdDeserializer<LocalDateTime> {

	private static final DateTimeFormatter LEGACY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public EventOccurredAtDeserializer() {
		super(LocalDateTime.class);
	}

	@Override
	public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		String value = parser.getValueAsString();
		if (value == null || value.isBlank()) {
			return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
		}
		try {
			return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		}
		catch (DateTimeParseException ignored) {
			try {
				return LocalDateTime.parse(value, LEGACY_FORMAT);
			}
			catch (DateTimeParseException exception) {
				throw JsonMappingException.from(parser,
						"occurredAt 必须使用 ISO-8601（例如 2026-07-16T14:30:00）或 yyyy-MM-dd HH:mm:ss 格式");
			}
		}
	}

}
