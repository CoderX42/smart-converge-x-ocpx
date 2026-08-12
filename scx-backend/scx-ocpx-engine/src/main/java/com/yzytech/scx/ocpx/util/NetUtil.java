package com.yzytech.scx.ocpx.util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;

public final class NetUtil {

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

	private NetUtil() {
	}

	public static String httpGet(String url, Map<String, Object> ignored) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
			HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				return response.body();
			}
			if (response.statusCode() == 204) {
				return "";
			}
		}
		catch (Exception exception) {
		}
		return null;
	}

	public static String strictUrlEncode(String value, Charset charset) {
		return URLEncoder.encode(value, charset).replace("+", "%20");
	}

}
