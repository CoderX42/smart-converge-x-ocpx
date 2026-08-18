package com.yzytech.scx.ocpx.admin.service;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class DefaultOcpxIntegrationCallbackHttpClient implements OcpxIntegrationCallbackHttpClient {

	private static final int RESPONSE_LIMIT_BYTES = 4096;

	private final HttpClient httpClient;

	private final Duration requestTimeout;

	private final int responseLimitBytes;

	public DefaultOcpxIntegrationCallbackHttpClient() {
		this(Duration.ofSeconds(3), Duration.ofSeconds(5), RESPONSE_LIMIT_BYTES);
	}

	private DefaultOcpxIntegrationCallbackHttpClient(Duration connectTimeout, Duration requestTimeout,
			int responseLimitBytes) {
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		this.requestTimeout = requestTimeout;
		this.responseLimitBytes = responseLimitBytes;
	}

	static DefaultOcpxIntegrationCallbackHttpClient forTesting(Duration connectTimeout, Duration requestTimeout,
			int responseLimitBytes) {
		return new DefaultOcpxIntegrationCallbackHttpClient(connectTimeout, requestTimeout, responseLimitBytes);
	}

	@Override
	public Response get(URI uri) {
		long startedAt = System.nanoTime();
		HttpRequest request = HttpRequest.newBuilder(uri)
			.GET()
			.timeout(requestTimeout)
			.header("User-Agent", "SCX-OCPX-INTEGRATION/1.0")
			.build();
		try {
			HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			String body;
			try (InputStream stream = response.body()) {
				byte[] bytes = stream.readNBytes(responseLimitBytes + 1);
				int length = Math.min(bytes.length, responseLimitBytes);
				body = new String(bytes, 0, length, StandardCharsets.UTF_8);
				if (bytes.length > responseLimitBytes) {
					body += "...";
				}
			}
			return new Response(response.statusCode(), body, null, elapsedMillis(startedAt));
		}
		catch (HttpTimeoutException exception) {
			return new Response(null, null, "CALLBACK_TIMEOUT", elapsedMillis(startedAt));
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return new Response(null, null, "CALLBACK_INTERRUPTED", elapsedMillis(startedAt));
		}
		catch (Exception exception) {
			return new Response(null, null, "CALLBACK_IO_ERROR", elapsedMillis(startedAt));
		}
	}

	private long elapsedMillis(long startedAt) {
		return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
	}

}
