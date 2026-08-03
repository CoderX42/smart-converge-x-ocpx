package com.yzytech.scx.ocpx.runtime.ingress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedFeedbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.FeedbackEnvelope;
import com.yzytech.scx.ocpx.runtime.model.IngressResult;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import com.yzytech.scx.ocpx.runtime.security.RequestAuthenticator;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class FeedbackIngressService {

	private static final int MAX_FEEDBACK_BYTES = 64 * 1024;

	private final ObjectMapper objectMapper;

	private final ObjectWriter canonicalWriter;

	private final RouteConfigurationCache routeCache;

	private final RequestAuthenticator authenticator;

	private final ProtocolCrypto protocolCrypto;

	private final RedisFeedbackIngress feedbackIngress;

	private final AdapterRegistry adapterRegistry;

	private final OcpxRuntimeProperties properties;

	public FeedbackIngressService(ObjectMapper objectMapper, RouteConfigurationCache routeCache,
			RequestAuthenticator authenticator, ProtocolCrypto protocolCrypto, RedisFeedbackIngress feedbackIngress,
			AdapterRegistry adapterRegistry, OcpxRuntimeProperties properties) {
		this.objectMapper = objectMapper;
		this.canonicalWriter = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
		this.routeCache = routeCache;
		this.authenticator = authenticator;
		this.protocolCrypto = protocolCrypto;
		this.feedbackIngress = feedbackIngress;
		this.adapterRegistry = adapterRegistry;
		this.properties = properties;
	}

	public IngressResult accept(String channelCode, String method, String path, String rawBody, HttpHeaders headers) {
		if (rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_FEEDBACK_BYTES) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "feedback payload is too large");
		}
		FeedbackReqV1 feedback;
		try {
			String adapterCode = routeCache.requireDownstreamAdapter(channelCode);
			feedback = adapterRegistry.requireDownstream(adapterCode).parseFeedback(rawBody);
		}
		catch (OcpxException e) {
			throw e;
		}
		catch (IllegalArgumentException e) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		String routeReference = firstNonBlank(feedback.getMonitorId(), feedback.getTaskId());
		if (routeReference == null) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "monitor_id or task_id is required");
		}
		if (routeReference.length() > 100) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "route reference is too long");
		}
		RouteSnapshot route = routeCache.require(channelCode, routeReference);
		authenticator.authenticate(route, method, path, rawBody, headers);
		if (feedback.getChannelId() != null && !feedback.getChannelId().isBlank()
				&& !List.of(route.channelId(), route.channelCode()).contains(feedback.getChannelId())) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "channel_id does not match route");
		}
		OcpxEventType eventType;
		try {
			eventType = OcpxEventType.fromExternal(feedback.getEvent());
		}
		catch (IllegalArgumentException e) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		String normalizedPayload;
		try {
			normalizedPayload = canonicalWriter.writeValueAsString(feedback);
		}
		catch (JsonProcessingException e) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "feedback cannot be normalized");
		}
		String payloadHash = OcpxHashing.sha256(normalizedPayload);
		String deviceKeyHash = deviceKeyHash(feedback);
		OffsetDateTime occurredAt = occurredAt(feedback.getEventTime());
		String eventId = firstNonBlank(feedback.getEventId(),
				eventType == OcpxEventType.CLICK ? feedback.getClickid() : null);
		if (eventId != null && eventId.length() > 128) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "event_id or clickid is too long");
		}
		if (eventId == null) {
			eventId = OcpxHashing.sha256(
					channelCode + "|" + route.routeId() + "|" + eventType.name() + "|" + payloadHash);
		}
		FeedbackEnvelope envelope;
		try {
			boolean forward = adapterRegistry.requireUpstream(route.upstreamAdapterCode()).supports(eventType);
			String callbackRef = forward ? UUID.randomUUID().toString().replace("-", "") : null;
			NormalizedFeedbackEvent normalized = new NormalizedFeedbackEvent(eventId, payloadHash, eventType,
					channelCode, route.routeId(), route.routeVersion(), occurredAt, OffsetDateTime.now(),
					deviceKeyHash);
			envelope = new FeedbackEnvelope(normalized.eventId(), normalized.payloadHash(), normalized.eventType(),
					normalized.channelCode(), normalized.routeId(), normalized.routeVersion(), normalized.occurredAt(),
					normalized.receivedAt(), normalized.deviceKeyHash(), callbackRef,
					protocolCrypto.encrypt(normalizedPayload),
					protocolCrypto.encrypt(objectMapper.writeValueAsString(route)), forward);
		}
		catch (JsonProcessingException | IllegalArgumentException e) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "feedback cannot be normalized");
		}
		return feedbackIngress.enqueue(envelope);
	}

	private String deviceKeyHash(FeedbackReqV1 feedback) {
		String candidate = firstNonBlank(feedback.getOaid(), feedback.getOaidMd5(), feedback.getIdfa(),
				feedback.getIdfaMd5(), feedback.getImei(), feedback.getImeiMd5(), feedback.getCaid(),
				feedback.getAndroidId(), feedback.getAndroidIdMd5(), feedback.getAaid(), feedback.getGaid(),
				feedback.getIdfv());
		return candidate == null ? null : OcpxHashing.hmacSha256(properties.getDeviceHmacKey(), candidate.trim());
	}

	private OffsetDateTime occurredAt(Long epoch) {
		if (epoch == null) {
			return OffsetDateTime.now();
		}
		try {
			long millis = epoch < 10_000_000_000L ? Math.multiplyExact(epoch, 1000) : epoch;
			Instant instant = Instant.ofEpochMilli(millis);
			if (instant.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
					|| instant.isAfter(Instant.now().plusSeconds(86400))) {
				throw new OcpxException(HttpStatus.BAD_REQUEST, "event_time is outside the accepted range");
			}
			return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
		}
		catch (OcpxException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "event_time is invalid");
		}
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

}
