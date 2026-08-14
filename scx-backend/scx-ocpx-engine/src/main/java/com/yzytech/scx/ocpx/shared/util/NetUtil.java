package com.yzytech.scx.ocpx.shared.util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;

/** OCPX 上下游 GET 请求与 URL 编码工具。 */
public final class NetUtil {

	/** 复用连接的 JDK HTTP Client，连接超时一秒。 */
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

	private NetUtil() {
	}

	/**
	 * 发起同步 GET 请求。
	 *
	 * <p>
	 * 仅 200 返回响应体，204 返回空字符串；其他状态或异常返回 {@code null}。第二个参数只为兼容旧签名， 当前不会用于拼接请求参数。
	 * @param url 已完整拼接的请求地址
	 * @param ignored 兼容参数，当前忽略
	 * @return 响应内容、空字符串或 {@code null}
	 */
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

	/** 发起同步 GET，并保留 HTTP 状态与脱敏错误分类供协议审计使用。 */
	public static HttpGetResult httpGetResult(String url) {
		long startedAt = System.nanoTime();
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
			HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			return new HttpGetResult(response.statusCode(), response.body(), null, elapsedMillis(startedAt));
		}
		catch (java.net.http.HttpTimeoutException exception) {
			return new HttpGetResult(null, null, "TIMEOUT", elapsedMillis(startedAt));
		}
		catch (Exception exception) {
			return new HttpGetResult(null, null, "REQUEST_FAILED", elapsedMillis(startedAt));
		}
	}

	private static long elapsedMillis(long startedAt) {
		return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
	}

	/** 不包含请求地址或敏感参数的 HTTP GET 结果。 */
	public record HttpGetResult(Integer statusCode, String body, String errorCode, long durationMs) {
	}

	/**
	 * 按指定字符集进行表单 URL 编码，并把空格规范为 {@code %20}。
	 * @param value 待编码文本
	 * @param charset 字符集
	 * @return 严格百分号编码结果
	 */
	public static String strictUrlEncode(String value, Charset charset) {
		return URLEncoder.encode(value, charset).replace("+", "%20");
	}

}
