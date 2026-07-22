package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.tracking.config.TrackingProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpCallbackTransportTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null)
			server.stop(0);
	}

	@Test
	void signsExactPayloadAndStableIdempotencyKey() throws Exception {
		AtomicReference<com.sun.net.httpserver.HttpExchange> captured = new AtomicReference<>();
		AtomicReference<String> body = new AtomicReference<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/callback", exchange -> {
			captured.set(exchange);
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] response = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		String payload = "{\"eventType\":\"activate\"}";
		PostbackRequest request = new PostbackRequest(endpoint("/callback"), "job-1", payload, "scx_client", "v2",
				"channel-secret");

		transport().post(request);

		var headers = captured.get().getRequestHeaders();
		String timestamp = headers.getFirst("X-Timestamp");
		String nonce = headers.getFirst("X-Nonce");
		String canonical = "POST\n/callback\n" + timestamp + "\n" + nonce + "\n" + SignUtils.sha256(payload);
		assertThat(body.get()).isEqualTo(payload);
		assertThat(headers.getFirst("Idempotency-Key")).isEqualTo("job-1");
		assertThat(headers.getFirst("X-Client-Id")).isEqualTo("scx_client");
		assertThat(headers.getFirst("X-Key-Version")).isEqualTo("v2");
		assertThat(headers.getFirst("X-Sign")).isEqualTo(SignUtils.hmacSha256(canonical, "channel-secret"));
	}

	@Test
	void doesNotFollowRedirects() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().set("Location", endpoint("/callback"));
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		server.createContext("/callback", exchange -> {
			byte[] response = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		assertThatThrownBy(() -> transport()
			.post(new PostbackRequest(endpoint("/redirect"), "job-1", "{}", "client", "v1", "secret")))
			.isInstanceOf(CallbackDeliveryException.class)
			.hasMessageContaining("302");
	}

	private HttpCallbackTransport transport() {
		TrackingProperties properties = new TrackingProperties();
		properties.setCallbackConnectTimeoutMs(1_000);
		properties.setCallbackReadTimeoutMs(1_000);
		return new HttpCallbackTransport(new ObjectMapper(), properties);
	}

	private String endpoint(String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

}
