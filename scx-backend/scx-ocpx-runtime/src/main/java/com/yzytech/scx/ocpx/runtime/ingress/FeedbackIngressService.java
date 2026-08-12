package com.yzytech.scx.ocpx.runtime.ingress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.FeedbackReqV2;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.OcpxPartnerContext;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamDeliveryContext;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.config.CallbackTokenService;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.config.PartnerContextRepository;
import com.yzytech.scx.ocpx.runtime.config.PartnerCredentialResolver;
import com.yzytech.scx.ocpx.runtime.delivery.DeliveryHttpClient;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.IngressResult;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxFlowLogRepository;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

/** Synchronous media Feedback -> upstream advertiser forwarding. */
@Service
public class FeedbackIngressService {

	private static final int MAX_FEEDBACK_BYTES = 64 * 1024;

	private final ObjectMapper objectMapper;
	private final PartnerContextRepository contexts;
	private final PartnerCredentialResolver credentials;
	private final AdapterRegistry adapters;
	private final CallbackTokenService callbackTokens;
	private final OcpxRuntimeProperties properties;
	private final DeliveryHttpClient httpClient;
	private final OcpxFlowLogRepository flowLogs;
	private final ReactiveBlockingExecutor blockingExecutor;

	public FeedbackIngressService(ObjectMapper objectMapper, PartnerContextRepository contexts,
			PartnerCredentialResolver credentials, AdapterRegistry adapters, CallbackTokenService callbackTokens,
			OcpxRuntimeProperties properties, DeliveryHttpClient httpClient, OcpxFlowLogRepository flowLogs,
			ReactiveBlockingExecutor blockingExecutor) {
		this.objectMapper = objectMapper;
		this.contexts = contexts;
		this.credentials = credentials;
		this.adapters = adapters;
		this.callbackTokens = callbackTokens;
		this.properties = properties;
		this.httpClient = httpClient;
		this.flowLogs = flowLogs;
		this.blockingExecutor = blockingExecutor;
	}

	public Mono<IngressResult> accept(String mediaCode, String method, String path, String rawBody,
			HttpHeaders headers) {
		if (!StringUtils.hasText(rawBody) || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_FEEDBACK_BYTES) {
			return Mono.error(new OcpxException(HttpStatus.BAD_REQUEST, "feedback payload is empty or too large"));
		}
		return blockingExecutor.call(() -> prepare(mediaCode, method, path, rawBody, headers))
			.flatMap(this::deliver)
			.onErrorMap(JsonProcessingException.class,
					error -> new OcpxException(HttpStatus.BAD_REQUEST, "feedback payload is not valid JSON"))
			.onErrorMap(IllegalArgumentException.class,
					error -> new OcpxException(HttpStatus.BAD_REQUEST, error.getMessage()))
			.onErrorMap(IllegalStateException.class,
					error -> new OcpxException(HttpStatus.SERVICE_UNAVAILABLE,
							"partner configuration is unavailable"));
	}

	private Work prepare(String mediaCode, String method, String path, String rawBody, HttpHeaders headers)
			throws Exception {
		FeedbackReqV2 feedback = objectMapper.readValue(rawBody, FeedbackReqV2.class);
		validate(feedback);
		OcpxPartnerContext context = contexts.findForFeedback(mediaCode, feedback.getMonitorTaskId())
			.orElseThrow(() -> new OcpxException(HttpStatus.NOT_FOUND, "active monitor task not found"));
		authenticate(context, method, path, rawBody, headers);
		OcpxEventType eventType = OcpxEventType.fromExternalOrUnknown(feedback.getEvent());
		UpstreamAdapter adapter = adapters.requireUpstream(context.upstreamAdapterCode());
		if (!adapter.supports(eventType)) {
			throw new OcpxException(HttpStatus.UNPROCESSABLE_ENTITY, "upstream adapter does not support event");
		}
		PartnerCredentialResolver.ResolvedCredential credential = credentials.requireAdvertiser(
				context.advertiserCredentialConfigKey(), context.advertiserCredentialVersion());
		String callbackToken = callbackTokens.issue(context.monitorTaskId(), feedback.getEventId(),
				feedback.getClickId());
		String callbackUrl = properties.getPublicBaseUrl().replaceAll("/$", "")
				+ "/open-api/ocpx/v2/advertisers/" + context.advertiserCode() + "/callback?ext="
				+ UriUtils.encodeQueryParam(callbackToken, StandardCharsets.UTF_8);
		AdapterHttpRequest request = adapter.buildFeedback(new UpstreamDeliveryContext(context, feedback,
				feedback.getEvent(), callbackUrl, credential.account(), credential.secret()));
		String normalized = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.writeValueAsString(feedback);
		return new Work(context, feedback, adapter, request, OcpxHashing.sha256(normalized),
				occurredAt(feedback.getEventTime()), System.nanoTime());
	}

	private Mono<IngressResult> deliver(Work work) {
		return httpClient.exchange(work.request()).onErrorResume(error -> {
			long duration = elapsed(work.started());
			return log(work, "FAILED", null, safeMessage(error), duration)
				.then(Mono.error(new OcpxException(HttpStatus.BAD_GATEWAY, "upstream feedback delivery failed")));
		}).flatMap(response -> {
			AdapterDeliveryResult result = work.adapter().evaluateFeedbackResponse(response);
			long duration = elapsed(work.started());
			String message = result.success() ? "partner accepted" : result.reason();
			return log(work, result.success() ? "SUCCESS" : "FAILED", response.statusCode(), message, duration)
				.then(result.success()
						? Mono.just(new IngressResult(work.feedback().getEventId(), IngressResult.Status.ACCEPTED,
								OffsetDateTime.now(ZoneOffset.UTC)))
						: Mono.error(new OcpxException(HttpStatus.BAD_GATEWAY,
								"upstream rejected feedback: " + result.reason())));
		});
	}

	private Mono<Void> log(Work work, String status, Integer partnerHttpStatus, String message, long duration) {
		return blockingExecutor.run(() -> flowLogs.record(work.context(), "FEEDBACK", work.feedback().getEventId(),
				null, work.feedback().getEvent(), work.payloadHash(), status, partnerHttpStatus, message, duration,
				work.eventTime()));
	}

	private void validate(FeedbackReqV2 feedback) {
		if (feedback == null || !StringUtils.hasText(feedback.getMonitorTaskId())
				|| !StringUtils.hasText(feedback.getEvent()) || !StringUtils.hasText(feedback.getEventId())
				|| feedback.getEventTime() == null) {
			throw new IllegalArgumentException("monitor_task_id, event, event_id and event_time are required");
		}
		if (!"2.0".equals(feedback.getSchemaVersion())) {
			throw new IllegalArgumentException("schema_version must be 2.0");
		}
	}

	private void authenticate(OcpxPartnerContext context, String method, String path, String body,
			HttpHeaders headers) {
		PartnerCredentialResolver.ResolvedCredential credential = credentials.requireMedia(context.mediaAuthType(),
				context.mediaCredentialConfigKey(), context.mediaCredentialVersion());
		if ("NONE".equals(context.mediaAuthType())) {
			return;
		}
		if ("TOKEN".equals(context.mediaAuthType())) {
			if (!constantTime("Bearer " + credential.secret(), headers.getFirst(HttpHeaders.AUTHORIZATION))) {
				throw new OcpxException(HttpStatus.UNAUTHORIZED, "media token is invalid");
			}
			return;
		}
		String timestamp = headers.getFirst("X-OCPX-Timestamp");
		String nonce = headers.getFirst("X-OCPX-Nonce");
		String supplied = headers.getFirst("X-OCPX-Signature");
		if (!Long.toString(context.mediaCredentialVersion()).equals(headers.getFirst("X-OCPX-Key-Version"))
				|| !validTimestamp(timestamp) || !StringUtils.hasText(nonce)) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, "media signature metadata is invalid");
		}
		String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n"
				+ OcpxHashing.sha256(body);
		if (!constantTime(hmac(credential.secret(), canonical), supplied)) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, "media signature is invalid");
		}
	}

	private boolean validTimestamp(String value) {
		try {
			long supplied = Long.parseLong(value);
			long millis = supplied < 10_000_000_000L ? Math.multiplyExact(supplied, 1000L) : supplied;
			return Math.abs(System.currentTimeMillis() - millis) <= 300_000;
		}
		catch (Exception ignored) {
			return false;
		}
	}

	private OffsetDateTime occurredAt(long epoch) {
		long millis = epoch < 10_000_000_000L ? Math.multiplyExact(epoch, 1000L) : epoch;
		Instant instant = Instant.ofEpochMilli(millis);
		if (instant.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
				|| instant.isAfter(Instant.now().plusSeconds(86400))) {
			throw new IllegalArgumentException("event_time is outside the accepted range");
		}
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private String hmac(String secret, String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("cannot validate media signature", error);
		}
	}

	private boolean constantTime(String expected, String actual) {
		return expected != null && actual != null && java.security.MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}

	private long elapsed(long started) {
		return Math.max(0, (System.nanoTime() - started) / 1_000_000);
	}

	private String safeMessage(Throwable error) {
		return "transport error: " + error.getClass().getSimpleName();
	}

	private record Work(OcpxPartnerContext context, FeedbackReqV2 feedback, UpstreamAdapter adapter,
			AdapterHttpRequest request, String payloadHash, OffsetDateTime eventTime, long started) {
	}

}
