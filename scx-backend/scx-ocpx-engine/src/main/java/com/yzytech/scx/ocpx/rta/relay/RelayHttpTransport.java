package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.protocol.security.ProtocolAddressPolicy;
import okhttp3.*;
import org.springframework.stereotype.Component;
import java.net.*;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

/**
 * Validated DNS answers are pinned to the actual socket; redirects/proxy/retry disabled.
 */
@Component
public class RelayHttpTransport implements RelayTransport {

	private final RelayProperties properties;

	private final RelaySecurity security;

	private final ExecutorService dns = new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64),
			r -> {
				Thread t = new Thread(r, "rta-dns");
				t.setDaemon(true);
				return t;
			}, new ThreadPoolExecutor.AbortPolicy());

	public RelayHttpTransport(RelayProperties properties, RelaySecurity security) {
		this.properties = properties;
		this.security = security;
	}

	@Override
	public int post(String endpoint, String credentialRef, String body, long timeoutMillis) {
		return exchange(new RtaUpstreamAdapter.Outbound(endpoint, credentialRef, body), timeoutMillis).status();
	}

	@Override
	public RtaUpstreamAdapter.HttpResult exchange(RtaUpstreamAdapter.Outbound outbound, long timeoutMillis) {
		return exchange(outbound, timeoutMillis, java.util.Map.of());
	}

	public RtaUpstreamAdapter.HttpResult uploadOppo(String body, java.util.Map<String, String> headers) {
		return exchange(
				new RtaUpstreamAdapter.Outbound("https://api.ads.heytapmobi.com/api/uploadActiveData", "", body), 5000,
				headers);
	}

	private RtaUpstreamAdapter.HttpResult exchange(RtaUpstreamAdapter.Outbound outbound, long timeoutMillis,
			java.util.Map<String, String> extraHeaders) {
		String endpoint = outbound.endpoint(), credentialRef = outbound.credentialRef(), body = outbound.body();
		long start = System.nanoTime();
		URI uri = URI.create(endpoint);
		if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
				|| uri.getFragment() != null || !validQuery(uri, outbound.queryKeys())
				|| uri.getPort() != -1 && uri.getPort() != 443
				|| !properties.getAllowedHosts().contains(uri.getHost().toLowerCase(Locale.ROOT)))
			throw new RelayException(400, "RTA_ENDPOINT_REJECTED");
		Future<InetAddress[]> lookup = null;
		try {
			lookup = dns.submit(() -> InetAddress.getAllByName(uri.getHost()));
			InetAddress[] addresses = lookup.get(Math.max(1, Math.min(500, timeoutMillis)), TimeUnit.MILLISECONDS);
			if (addresses.length == 0 || addresses.length > 16
					|| java.util.Arrays.stream(addresses).anyMatch(a -> !ProtocolAddressPolicy.isPublic(a)))
				throw new RelayException(400, "RTA_ADDRESS_REJECTED");
			long remaining = timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			if (remaining <= 0)
				throw new RelayException(504, "RTA_TRANSPORT_TIMEOUT");
			OkHttpClient client = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
				.followRedirects(false)
				.followSslRedirects(false)
				.retryOnConnectionFailure(false)
				.dns(host -> {
					if (!uri.getHost().equals(host))
						throw new UnknownHostException();
					return List.of(addresses);
				})
				.connectionPool(new ConnectionPool(0, 1, TimeUnit.SECONDS))
				.callTimeout(Duration.ofMillis(remaining))
				.build();
			Request.Builder request = new Request.Builder().url(endpoint)
				.post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")));
			extraHeaders.forEach(request::header);
			if (credentialRef != null && !credentialRef.isEmpty())
				security.headers(credentialRef, uri.getRawPath().isEmpty() ? "/" : uri.getRawPath(), body)
					.forEach(request::header);
			try (Response response = client.newCall(request.build()).execute()) {
				byte[] bytes = response.body() == null ? new byte[0] : response.body().byteStream().readNBytes(65537);
				if (bytes.length > 65536)
					throw new RelayException(502, "RTA_RESPONSE_TOO_LARGE");
				String content = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
					.decode(java.nio.ByteBuffer.wrap(bytes))
					.toString();
				return new RtaUpstreamAdapter.HttpResult(response.code(), content);
			}
			finally {
				client.connectionPool().evictAll();
				client.dispatcher().executorService().shutdown();
			}
		}
		catch (RelayException e) {
			throw e;
		}
		catch (Exception e) {
			throw new RelayException(503, "RTA_TRANSPORT_FAILED");
		}
		finally {
			if (lookup != null)
				lookup.cancel(true);
		}
	}

	static boolean validQuery(URI uri, java.util.Set<String> allowed) {
		if (uri.getRawQuery() == null)
			return allowed.isEmpty();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (String pair : uri.getRawQuery().split("&", -1)) {
			String[] parts = pair.split("=", 2);
			if (parts.length != 2 || parts[1].isEmpty() || !allowed.contains(parts[0]) || !seen.add(parts[0]))
				return false;
		}
		return seen.equals(allowed);
	}

	@jakarta.annotation.PreDestroy
	public void close() {
		dns.shutdownNow();
	}

}
