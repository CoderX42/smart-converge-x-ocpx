package com.yzytech.scx.ocpx.admin.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultOcpxMonitorTaskTestHttpClientTest {

	private HttpServer server;

	private String baseUrl;

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ok", exchange -> respond(exchange, 200, "accepted"));
		server.createContext("/failed", exchange -> respond(exchange, 500, "failed"));
		server.createContext("/large", exchange -> respond(exchange, 200, "0123456789"));
		server.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().add("Location", "/ok");
			respond(exchange, 302, "redirect");
		});
		server.createContext("/slow", exchange -> {
			try {
				Thread.sleep(200);
				respond(exchange, 200, "late");
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		});
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void returnsEngine200And500WithoutReclassifyingTheResponse() {
		DefaultOcpxMonitorTaskTestHttpClient client = client(Duration.ofSeconds(1), 64);

		assertThat(client.get(URI.create(baseUrl + "/ok"))).satisfies(response -> {
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).isEqualTo("accepted");
			assertThat(response.errorCode()).isNull();
		});
		assertThat(client.get(URI.create(baseUrl + "/failed"))).satisfies(response -> {
			assertThat(response.statusCode()).isEqualTo(500);
			assertThat(response.body()).isEqualTo("failed");
			assertThat(response.errorCode()).isNull();
		});
	}

	@Test
	void doesNotFollowRedirectsAndCapsResponseBodies() {
		DefaultOcpxMonitorTaskTestHttpClient client = client(Duration.ofSeconds(1), 5);

		assertThat(client.get(URI.create(baseUrl + "/redirect")).statusCode()).isEqualTo(302);
		assertThat(client.get(URI.create(baseUrl + "/large")).body()).isEqualTo("01234...");
	}

	@Test
	void returnsStableTimeoutCode() {
		DefaultOcpxMonitorTaskTestHttpClient client = client(Duration.ofMillis(50), 64);

		assertThat(client.get(URI.create(baseUrl + "/slow"))).satisfies(response -> {
			assertThat(response.statusCode()).isNull();
			assertThat(response.errorCode()).isEqualTo("ENGINE_TIMEOUT");
		});
	}

	private DefaultOcpxMonitorTaskTestHttpClient client(Duration requestTimeout, int responseLimitBytes) {
		return DefaultOcpxMonitorTaskTestHttpClient.forTesting(Duration.ofSeconds(1), requestTimeout,
				responseLimitBytes);
	}

	private void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

}
