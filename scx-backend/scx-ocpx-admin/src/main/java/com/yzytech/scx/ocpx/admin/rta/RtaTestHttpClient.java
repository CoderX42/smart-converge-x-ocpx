package com.yzytech.scx.ocpx.admin.rta;

import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * One bounded request to the configured Engine. No redirects or automatic application
 * retries.
 */
@Component
public class RtaTestHttpClient {

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(2))
		.followRedirects(HttpClient.Redirect.NEVER)
		.proxy(new ProxySelector() {
			public List<Proxy> select(URI uri) {
				return List.of(Proxy.NO_PROXY);
			}

			public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) {
			}
		})
		.build();

	public record Response(Integer status, String body, String error, long durationMs) {
	}

	public Response post(URI endpoint, String body) {
		long start = System.nanoTime();
		CompletableFuture<HttpResponse<String>> pending = null;
		try {
			var request = HttpRequest.newBuilder(endpoint)
				.timeout(Duration.ofSeconds(8))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
			pending = client.sendAsync(request, info -> new LimitedBody());
			var response = pending.get(8, TimeUnit.SECONDS);
			return new Response(response.statusCode(), response.body(), null, elapsed(start));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Response(null, null, "ENGINE_INTERRUPTED", elapsed(start));
		}
		catch (Exception e) {
			return new Response(null, null, "ENGINE_RESPONSE_UNKNOWN", elapsed(start));
		}
		finally {
			if (pending != null && !pending.isDone())
				pending.cancel(true);
		}
	}

	private static long elapsed(long start) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
	}

	static class LimitedBody implements HttpResponse.BodySubscriber<String> {

		final CompletableFuture<String> result = new CompletableFuture<>();

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		Flow.Subscription subscription;

		public CompletionStage<String> getBody() {
			return result;
		}

		public void onSubscribe(Flow.Subscription s) {
			subscription = s;
			s.request(1);
		}

		public void onNext(List<ByteBuffer> chunks) {
			for (ByteBuffer chunk : chunks) {
				if (bytes.size() + chunk.remaining() > 65536) {
					subscription.cancel();
					result.completeExceptionally(new IllegalStateException("ENGINE_RESPONSE_TOO_LARGE"));
					return;
				}
				byte[] value = new byte[chunk.remaining()];
				chunk.get(value);
				bytes.writeBytes(value);
			}
			subscription.request(1);
		}

		public void onError(Throwable error) {
			result.completeExceptionally(error);
		}

		public void onComplete() {
			try {
				result.complete(
						StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString());
			}
			catch (Exception e) {
				result.completeExceptionally(e);
			}
		}

	}

}
