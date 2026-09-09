package com.yzytech.scx.ocpx.admin.rta;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class RtaTestHttpClientTest {

	@Test
	void doesNotFollowRedirectAndRejectsOversizedBody() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var targets = new AtomicInteger();
		server.createContext("/redirect", exchange -> {
			exchange.getRequestBody().readAllBytes();
			exchange.getResponseHeaders().set("Location", "/target");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		server.createContext("/target", exchange -> {
			targets.incrementAndGet();
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.createContext("/large", exchange -> {
			exchange.getRequestBody().readAllBytes();
			byte[] body = "x".repeat(70000).getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try {
				exchange.getResponseBody().write(body);
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		try {
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			var client = new RtaTestHttpClient();
			assertThat(client.post(URI.create(base + "/redirect"), "{}").status()).isEqualTo(302);
			assertThat(targets.get()).isZero();
			assertThat(client.post(URI.create(base + "/large"), "{}").error()).isEqualTo("ENGINE_RESPONSE_UNKNOWN");
		}
		finally {
			server.stop(0);
		}
	}

}
