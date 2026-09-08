package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import com.yzytech.scx.ocpx.rta.admission.RtaGate;
import com.yzytech.scx.ocpx.rta.exception.RtaAdmissionDeniedException;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Launches an ephemeral local Redis and local HTTP simulators. Never loads application
 * profiles.
 */
class RelayIntegrationTest {

	static Process process;
	static LettuceConnectionFactory factory;
	static StringRedisTemplate redis;

	RelayStore store;

	RelayRepository repository;

	RelaySecurity security;

	RelayService service;

	RtaAdapterRegistry adapters = new RtaAdapterRegistry(
			List.of(new GenericAsyncRtaAdapter(), new WeifengRtaAdapter()));

	RelayProperties properties;

	RelayAudit audit;

	Media media = new Media("media-a", "https://media.test/notify", "media-key", 1, true);

	Upstream upstream = new Upstream("upstream-a", "advertiser-a", "advertiser-code", "https://advertiser.test/request",
			"upstream-key", 1, 5000, 300, true);

	@BeforeAll
	static void startRedis() throws Exception {
		int port;
		try (var socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		String executable = System.getProperty("rta.redis.executable", "redis-server");
		process = new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", Integer.toString(port), "--save", "",
				"--appendonly", "no")
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
		factory = new LettuceConnectionFactory("127.0.0.1", port);
		factory.afterPropertiesSet();
		factory.start();
		redis = new StringRedisTemplate(factory);
		redis.afterPropertiesSet();
		for (int i = 0; i < 100; i++) {
			try {
				redis.getConnectionFactory().getConnection().ping();
				return;
			}
			catch (Exception e) {
				Thread.sleep(20);
			}
		}
		throw new IllegalStateException("isolated Redis did not start");
	}

	@AfterAll
	static void stopRedis() {
		if (factory != null)
			factory.destroy();
		if (process != null)
			process.destroy();
	}

	@BeforeEach
	void setup() throws Exception {
		try (var connection = factory.getConnection()) {
			connection.serverCommands().flushDb();
		}
		properties = new RelayProperties();
		properties.setEnabled(true);
		properties.setCallbackBaseUrl("https://scx.test/ocpx");
		properties.setPayloadKey(Base64.getEncoder().encodeToString(new byte[32]));
		properties.setCredentials(Map.of("media-key", "media-secret-012345678901234567890123456789", "upstream-key",
				"upstream-secret-012345678901234567890123456"));
		store = new RelayStore(redis);
		repository = mock(RelayRepository.class);
		security = new RelaySecurity(properties, redis);
		audit = new RelayAudit(redis, new SimpleMeterRegistry());
		service = new RelayService(repository, store, security, properties, audit, adapters);
		when(repository.mediaByCredential("media-key")).thenReturn(media);
		when(repository.upstream("id", "upstream-a")).thenReturn(upstream);
		when(repository.upstream("access_code", "advertiser-code")).thenReturn(upstream);
		for (String task : List.of("A", "B", "C")) {
			when(repository.binding(task, "media-a"))
				.thenReturn(new Binding(task, "advertiser-a", "media-a", "link-a", "upstream-a", "product", task, 1));
			when(repository.taskRequiresRta(task)).thenReturn(true);
		}
	}

	Request request(String id) {
		return new Request(id, List.of("A", "B", "C"), Map.of("oaid", "unchanged-device-business-value"),
				Map.of("os", "android"));
	}

	@Test
	void idempotencyEncryptionCandidateMappingAndGate() {
		Accepted first = service.accept(request("request-a"), media);
		assertThat(service.accept(request("request-a"), media)).isEqualTo(first);
		assertThatThrownBy(
				() -> service.accept(new Request("request-a", List.of("A"), Map.of("oaid", "other"), Map.of()), media))
			.isInstanceOf(RelayException.class)
			.hasMessage("RTA_REQUEST_CONFLICT");
		assertThat(redis.opsForValue().get(RtaContract.PREFIX + "state:" + first.relayRequestId()))
			.doesNotContain("unchanged-device-business-value");
		assertThat(store.get(first.relayRequestId()).getMatchedTaskIds()).isEmpty();
		Decision decision = new Decision(first.relayRequestId(), "ALLOW",
				List.of(new Candidate("product", "A"), new Candidate("product", "C")));
		assertThat(service.decide(decision, "advertiser-code").getMatchedTaskIds()).containsExactly("A", "C");
		assertThat(service.decide(decision, "advertiser-code").getStatus()).isEqualTo("ALLOW");
		assertThat(store.get(first.relayRequestId()).getEncryptedPayload()).isNull();
		assertThatThrownBy(
				() -> service.decide(new Decision(first.relayRequestId(), "DENY", List.of()), "advertiser-code"))
			.hasMessage("RTA_REQUEST_CONFLICT");
		RtaGate gate = new RtaGate(repository, store, audit);
		FeedbackReq event = FeedbackReq.builder()
			.mId("media-a")
			.aId("advertiser-a")
			.lId("link-a")
			.tId("A")
			.event(EventTypeEnum.CLICK)
			.reqId("different-click-id")
			.rtaRequestId(first.relayRequestId())
			.build();
		gate.requireAllowed(event);
		assertThat(event.isRtaProtected()).isTrue();
		event.setTId("B");
		assertThatThrownBy(() -> gate.requireAllowed(event)).isInstanceOf(RtaAdmissionDeniedException.class);
		event.setTId("A");
		event.setMId("media-b");
		assertThatThrownBy(() -> gate.requireAllowed(event)).isInstanceOf(RtaAdmissionDeniedException.class);
		event.setMId("media-a");
		event.setRtaRequestId(null);
		assertThatThrownBy(() -> gate.requireAllowed(event)).hasMessage("RTA_ADMISSION_MISSING");
		var metric = redis.opsForHash()
			.entries(RtaContract.PREFIX + "metric:" + (store.get(first.relayRequestId()).getReceivedAt() / 3600000)
					+ ":media-a:B:upstream-a");
		assertThat(metric).containsEntry("requestCount", "1").containsEntry("denyCount", "1");
	}

	@Test
	void timeoutUnknownCandidateAndNotificationExhaustion() throws Exception {
		var accepted = service.accept(request("timeout"), media);
		var pending = store.get(accepted.relayRequestId());
		store.finish(accepted.relayRequestId(), "TIMEOUT", "TIMEOUT", List.of(), "TIMEOUT", pending.getDeadline());
		RelayStore restarted = new RelayStore(redis);
		assertThat(restarted.claim("send", pending.getDeadline() + 100, 10)).isEmpty();
		assertThat(restarted.claim("notify", pending.getDeadline() + 100, 10)).contains(accepted.relayRequestId());
		assertThatThrownBy(() -> service.decide(
				new Decision(accepted.relayRequestId(), "ALLOW", List.of(new Candidate("product", "A"))),
				"advertiser-code"))
			.hasMessage("RTA_REQUEST_EXPIRED");
		long time = pending.getDeadline();
		for (int delay : List.of(0, 1000, 3000, 10000)) {
			time += delay;
			restarted.mutate("DELIVERY", time, accepted.relayRequestId(), "false");
		}
		assertThat(restarted.get(accepted.relayRequestId()).getDeliveryStatus()).isEqualTo("FAILED");
		assertThat(restarted.get(accepted.relayRequestId()).getDeliveryAttempts()).isEqualTo(4);
		assertThat(restarted.get(accepted.relayRequestId()).getEncryptedPayload()).isNull();
		var unknown = service.accept(request("unknown"), media);
		assertThatThrownBy(() -> service.decide(
				new Decision(unknown.relayRequestId(), "ALLOW", List.of(new Candidate("product", "outside"))),
				"advertiser-code"))
			.hasMessage("RTA_MATCH_INVALID");
		assertThat(store.get(unknown.relayRequestId()).getStatus()).isEqualTo("ERROR");
	}

	@Test
	void signatureRejectsBodyTamperingReplayAndUnknownFields() {
		String body = RelayJson.write(request("signed")), path = "/ocpx/rta/requests";
		Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		headers.putAll(security.headers("media-key", path, body));
		assertThatThrownBy(() -> security.verify("media-key", path, body + " ", headers, System.currentTimeMillis()))
			.hasMessage("RTA_SIGNATURE_INVALID");
		security.verify("media-key", path, body, headers, System.currentTimeMillis());
		assertThatThrownBy(() -> security.verify("media-key", path, body, headers, System.currentTimeMillis()))
			.hasMessage("RTA_REPLAY");
		assertThatThrownBy(
				() -> RelayJson.read(body.substring(0, body.length() - 1) + ",\"mediaId\":\"spoofed\"}", Request.class))
			.hasMessage("RTA_JSON_INVALID");
		String encrypted = security.encrypt("one", body);
		assertThat(security.decrypt("one", encrypted)).isEqualTo(body);
		assertThatThrownBy(() -> security.decrypt("two", encrypted)).hasMessage("RTA_PAYLOAD_KEY_UNAVAILABLE");
	}

	@Test
	void concurrentConflictingDecisionsProduceExactlyOneTerminalMetric() throws Exception {
		var accepted = service.accept(request("racing"), media);
		var allow = new Decision(accepted.relayRequestId(), "ALLOW", List.of(new Candidate("product", "A")));
		var deny = new Decision(accepted.relayRequestId(), "DENY", List.of());
		var gate = new CountDownLatch(1);
		var futures = List.of(allow, deny).stream().map(decision -> CompletableFuture.supplyAsync(() -> {
			try {
				gate.await();
				service.decide(decision, "advertiser-code");
				return "SUCCESS";
			}
			catch (RelayException e) {
				return e.getMessage();
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		})).toList();
		gate.countDown();
		assertThat(List.of(futures.get(0).get(), futures.get(1).get())).containsExactlyInAnyOrder("SUCCESS",
				"RTA_REQUEST_CONFLICT");
		var state = store.get(accepted.relayRequestId());
		var metric = redis.opsForHash()
			.entries(RtaContract.PREFIX + "metric:" + (state.getReceivedAt() / 3600000) + ":media-a:A:upstream-a");
		long done = Long.parseLong(metric.getOrDefault("allowCount", "0").toString())
				+ Long.parseLong(metric.getOrDefault("denyCount", "0").toString());
		assertThat(done).isEqualTo(1);
		assertThat(metric.get("requestCount")).isEqualTo("1");
	}

	@Test
	void successSamplingIsStableAndFailuresAreAlwaysRedacted() {
		var accepted = service.accept(request("sampling"), media);
		var state = store.get(accepted.relayRequestId());
		redis.delete(RtaContract.PREFIX + "logs");
		state.setRelayRequestId("00000000000000000000000000000000");
		boolean sampled = Math.floorMod(state.getRelayRequestId().hashCode(), 10) == 0;
		audit.write(state, "MEDIA_ACCEPTED", true, "ACCEPTED", 0);
		audit.write(state, "UPSTREAM_SUBMIT", true, "HTTP_202", 1);
		assertThat(redis.opsForStream().size(RtaContract.PREFIX + "logs")).isEqualTo(sampled ? 6L : 0L);
		audit.write(state, "UPSTREAM_SUBMIT", false, "HTTP_503", 1);
		assertThat(redis.opsForStream().size(RtaContract.PREFIX + "logs")).isEqualTo(sampled ? 9L : 3L);
		assertThat(redis.opsForStream()
			.range(RtaContract.PREFIX + "logs", org.springframework.data.domain.Range.unbounded())
			.toString()).doesNotContain("unchanged-device-business-value", "secret", "encryptedPayload");
	}

	@Test
	void numericBusinessValuesAndTrailingJsonAreRejected() {
		String body = RelayJson.write(request("strict"));
		assertThatThrownBy(() -> RelayJson.read(body + " {}", Request.class)).hasMessage("RTA_JSON_INVALID");
		assertThatThrownBy(
				() -> RelayJson.read(body.replace("\"unchanged-device-business-value\"", "123"), Request.class))
			.hasMessage("RTA_JSON_INVALID");
		assertThatThrownBy(
				() -> RelayService.validate(new Request("bad", List.of("A", "A"), Map.of("oaid", "x"), Map.of())))
			.hasMessage("RTA_REQUEST_INVALID");
	}

	@Test
	void advertiserCallsBackOverSeparateHttpBeforeAcceptanceResponseAndMediaReceivesOnlyAC() throws Exception {
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new RelayController(service, repository, security, adapters))
			.build();
		var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var executor = Executors.newCachedThreadPool();
		server.setExecutor(executor);
		String origin = "http://127.0.0.1:" + server.getAddress().getPort();
		HttpClient client = HttpClient.newHttpClient();
		AtomicReference<String> notified = new AtomicReference<>();
		server.createContext("/ocpx/rta", exchange -> {
			try {
				var builder = post(exchange.getRequestURI().getPath()).contextPath("/ocpx")
					.contentType("application/json")
					.content(exchange.getRequestBody().readAllBytes());
				exchange.getRequestHeaders().forEach((key, values) -> builder.header(key, values.toArray()));
				var response = mvc.perform(builder).andReturn().getResponse();
				byte[] out = response.getContentAsByteArray();
				exchange.sendResponseHeaders(response.getStatus(), out.length);
				exchange.getResponseBody().write(out);
			}
			catch (Exception e) {
				exchange.sendResponseHeaders(500, -1);
			}
			finally {
				exchange.close();
			}
		});
		server.createContext("/request", exchange -> {
			try {
				var payload = RelayJson.MAPPER.readTree(exchange.getRequestBody().readAllBytes());
				assertThat(payload.path("device").path("oaid").asText()).isEqualTo("unchanged-device-business-value");
				String callbackBody = RelayJson.write(new Decision(payload.path("relayRequestId").asText(), "ALLOW",
						List.of(new Candidate("product", "A"), new Candidate("product", "C"))));
				String callbackPath = "/ocpx/rta/callbacks/advertiser-code";
				var callback = HttpRequest.newBuilder(URI.create(origin + callbackPath))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(callbackBody));
				security.headers("upstream-key", callbackPath, callbackBody).forEach(callback::header);
				assertThat(client.send(callback.build(), HttpResponse.BodyHandlers.ofString()).statusCode())
					.isEqualTo(200);
				exchange.sendResponseHeaders(202, -1);
			}
			catch (Exception e) {
				exchange.sendResponseHeaders(500, -1);
			}
			finally {
				exchange.close();
			}
		});
		server.createContext("/notify", exchange -> {
			notified.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();
		RelayTransport simulatorTransport = (endpoint, ref, payload, timeout) -> {
			try {
				String path = URI.create(endpoint).getPath();
				var request = HttpRequest.newBuilder(URI.create(origin + path))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(payload));
				security.headers(ref, path, payload).forEach(request::header);
				return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
		RelayWorker worker = new RelayWorker(store, security, properties, simulatorTransport, repository, audit,
				new SimpleMeterRegistry(), adapters, service);
		try {
			String body = RelayJson.write(request("http-roundtrip"));
			String path = "/ocpx/rta/requests";
			var incoming = HttpRequest.newBuilder(URI.create(origin + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body));
			security.headers("media-key", path, body).forEach(incoming::header);
			var response = client.send(incoming.build(), HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).isEqualTo(202);
			Accepted accepted = RelayJson.read(response.body(), Accepted.class);
			worker.run("send", accepted.relayRequestId());
			assertThat(store.get(accepted.relayRequestId()).getStatus()).isEqualTo("ALLOW");
			assertThat(store.get(accepted.relayRequestId()).getEncryptedPayload()).isNull();
			worker.run("notify", accepted.relayRequestId());
			Notification notification = RelayJson.read(notified.get(), Notification.class);
			assertThat(notification.matchedTaskIds()).containsExactly("A", "C");
			assertThat(notification.requestId()).isEqualTo("http-roundtrip");
			assertThat(store.get(accepted.relayRequestId()).getDeliveryStatus()).isEqualTo("DELIVERED");
		}
		finally {
			worker.close();
			server.stop(0);
			executor.shutdownNow();
		}
	}

	void useWeifeng() {
		upstream = new WeifengRtaAdapterTest().upstream();
		upstream = new Upstream("upstream-a", "advertiser-a", "advertiser-code", upstream.endpoint(), "", 1, 5000, 300,
				true, "WEIFENG_RTA", 1, upstream.protocolConfig());
		when(repository.upstream("id", "upstream-a")).thenReturn(upstream);
		when(repository.upstream("access_code", "advertiser-code")).thenReturn(upstream);
	}

	Request single(String id) {
		return new Request(id, List.of("A"), Map.of("oaid", "test"), Map.of("os", "android"));
	}

	@Test
	void weifengSyncHttpDecisionThenMediaNotificationAndGate() throws Exception {
		useWeifeng();
		var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var notified = new AtomicReference<String>();
		var received = new AtomicReference<String>();
		server.createContext("/rtaCommon/sendRta", exchange -> {
			received.set(exchange.getRequestURI().getRawQuery());
			var input = RelayJson.read(
					new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
					com.fasterxml.jackson.databind.JsonNode.class);
			byte[] response = RelayJson.write(Map.of("id", input.path("id").textValue(), "code", 0))
				.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.createContext("/notify", exchange -> {
			notified.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();
		String origin = "http://127.0.0.1:" + server.getAddress().getPort();
		RelayTransport transport = new RelayTransport() {
			public int post(String url, String ref, String body, long timeout) {
				return exchange(new RtaUpstreamAdapter.Outbound(url, ref, body), timeout).status();
			}

			public RtaUpstreamAdapter.HttpResult exchange(RtaUpstreamAdapter.Outbound outbound, long timeout) {
				try {
					URI uri = URI.create(outbound.endpoint());
					var request = HttpRequest
						.newBuilder(URI.create(
								origin + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())))
						.timeout(java.time.Duration.ofMillis(timeout))
						.POST(HttpRequest.BodyPublishers.ofString(outbound.body()))
						.build();
					var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
					return new RtaUpstreamAdapter.HttpResult(response.statusCode(), response.body());
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
		var worker = new RelayWorker(store, security, properties, transport, repository, audit,
				new SimpleMeterRegistry(), adapters, service);
		try {
			var accepted = service.accept(single("wf-roundtrip"), media);
			worker.run("send", accepted.relayRequestId());
			assertThat(received.get()).isEqualTo("aid=5103&did=zsyd");
			var result = store.get(accepted.relayRequestId());
			assertThat(result.getStatus()).isEqualTo("ALLOW");
			assertThat(result.getMatchedTaskIds()).containsExactly("A");
			assertThat(result.getEncryptedPayload()).isNull();
			worker.run("notify", accepted.relayRequestId());
			assertThat(store.get(accepted.relayRequestId()).getDeliveryStatus()).isEqualTo("DELIVERED");
			assertThat(notified.get()).contains("wf-roundtrip", "ALLOW");
			var event = FeedbackReq.builder()
				.mId("media-a")
				.aId("advertiser-a")
				.lId("link-a")
				.tId("A")
				.event(EventTypeEnum.CLICK)
				.reqId("event-id")
				.rtaRequestId(accepted.relayRequestId())
				.build();
			new RtaGate(repository, store, audit).requireAllowed(event);
			assertThat(event.getProviderRtaRequestId()).isEqualTo(accepted.relayRequestId());
			assertThat(event.getReqId()).isEqualTo("event-id");
			event.setTId("B");
			assertThatThrownBy(() -> new RtaGate(repository, store, audit).requireAllowed(event))
				.isInstanceOf(RtaAdmissionDeniedException.class);
		}
		finally {
			worker.close();
			server.stop(0);
		}
	}

	@Test
	void weifengUnknownDispatchIsNotRepeatedAfterRestart() throws Exception {
		useWeifeng();
		var accepted = service.accept(single("wf-crash"), media);
		store.mutate("DISPATCH_ONCE", System.currentTimeMillis(), accepted.relayRequestId());
		var transport = mock(RelayTransport.class);
		var worker = new RelayWorker(new RelayStore(redis), security, properties, transport, repository, audit,
				new SimpleMeterRegistry(), adapters, service);
		try {
			worker.run("send", accepted.relayRequestId());
			verifyNoInteractions(transport);
			var pending = store.get(accepted.relayRequestId());
			assertThat(pending.getUpstreamAttempts()).isEqualTo(1);
			assertThat(store
				.finish(accepted.relayRequestId(), "TIMEOUT", "RTA_TIMEOUT", List.of(), "TIMEOUT",
						pending.getDeadline() + 1)
				.getStatus()).isEqualTo("TIMEOUT");
		}
		finally {
			worker.close();
		}
		assertThatThrownBy(() -> service.accept(request("wf-batch"), media)).hasMessage("RTA_SINGLE_TASK_REQUIRED");
	}

}
