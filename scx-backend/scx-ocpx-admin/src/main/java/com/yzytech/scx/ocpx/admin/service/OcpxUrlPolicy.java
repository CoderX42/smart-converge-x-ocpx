package com.yzytech.scx.ocpx.admin.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OcpxUrlPolicy {

	private static final int MAX_URL_LENGTH = 5000;

	private static final Pattern MACRO = Pattern.compile("__[A-Za-z0-9_]{2,64}__");

	private static final Set<String> ALLOWED_MACROS = Set.of("__IMEI__", "__IMEI_MD5__", "__OAID__", "__OAID_MD5__",
			"__IP__", "__ANDROIDID__", "__ANDROIDID_MD5__", "__AAID__", "__UA__", "__OS__", "__MODEL__", "__CALLBACK__",
			"__CALLBACK_URL__", "__CLICKID__", "__CAMPAIGNID__", "__ADGROUPID__", "__CREATIVEID__", "__ADVERTISERID__",
			"__REQUEST_ID__", "__IDFA__", "__IDFA_MD5__", "__EVENT__", "__EVENTID__", "__EVENT_ID__",
			"__EVENTTYPE__", "__EVENT_TYPE__", "__EVENT_TIME__",
			"__SOURCE_EVENT_ID__", "__MONITOR_TASK_ID__", "__ACTION_TIME__", "__AMOUNT__", "__AMOUNT_RAW__");

	private static final Set<String> RESERVED_SUFFIXES = Set.of("localhost", "local", "internal", "home", "lan", "test",
			"example", "invalid", "onion");

	public Result validate(String value) {
		return validate(value, ALLOWED_MACROS);
	}

	/**
	 * Validates transport and URL structure while taking the macro vocabulary from a
	 * published Adapter capability. This overload never broadens the caller supplied
	 * capability set.
	 */
	public Result validate(String value, Set<String> allowedMacros) {
		List<String> issues = new ArrayList<>();
		if (!StringUtils.hasText(value)) {
			return blocked("URL_REQUIRED", issues);
		}
		String source = value.trim();
		if (source.length() > MAX_URL_LENGTH) {
			return blocked("URL_TOO_LONG", issues);
		}
		if (source.chars().anyMatch(character -> Character.isISOControl(character)) || source.contains("\\")) {
			return blocked("URL_CONTROL_CHARACTER", issues);
		}
		try {
			URI uri = URI.create(source).normalize();
			String scheme = lower(uri.getScheme());
			if (!("https".equals(scheme) || "http".equals(scheme))) {
				return blocked("URL_SCHEME_NOT_ALLOWED", issues);
			}
			if (uri.getUserInfo() != null) {
				return blocked("URL_USERINFO_FORBIDDEN", issues);
			}
			if (uri.getFragment() != null) {
				return blocked("URL_FRAGMENT_FORBIDDEN", issues);
			}
			String host = normalizeHost(uri.getHost());
			if (!StringUtils.hasText(host) || reservedHost(host) || unsafeLiteralAddress(host)) {
				return blocked("URL_HOST_NOT_PUBLIC", issues);
			}
			int port = uri.getPort();
			if (port == 0 || port > 65535) {
				return blocked("URL_PORT_INVALID", issues);
			}
			validateQueryKeys(uri.getRawQuery(), issues);
			LinkedHashSet<String> macros = extractMacros(source, issues,
					allowedMacros == null ? Set.of() : allowedMacros);
			if (!issues.isEmpty()) {
				return new Result(null, masked(scheme, host, port, uri.getRawQuery()), host, null, List.copyOf(macros),
						List.copyOf(issues), "BLOCK");
			}
			String normalized = normalizedUrl(uri, scheme, host, port);
			return new Result(normalized, masked(scheme, host, port, uri.getRawQuery()), host, sha256(normalized),
					List.copyOf(macros), List.of(), "PASS");
		}
		catch (IllegalArgumentException error) {
			return blocked("URL_SYNTAX_INVALID", issues);
		}
	}

	private Result blocked(String issue, List<String> existing) {
		List<String> issues = new ArrayList<>(existing);
		issues.add(issue);
		return new Result(null, null, null, null, List.of(), List.copyOf(issues), "BLOCK");
	}

	private String normalizeHost(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String host = value.trim().toLowerCase(Locale.ROOT);
		if (host.startsWith("[") && host.endsWith("]")) {
			host = host.substring(1, host.length() - 1);
		}
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.contains(":")) {
			return host;
		}
		String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
		if (ascii.length() > 253 || !ascii.contains(".")) {
			return null;
		}
		for (String label : ascii.split("\\.")) {
			if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
				return null;
			}
		}
		return ascii;
	}

	private boolean reservedHost(String host) {
		String last = host.substring(host.lastIndexOf('.') + 1);
		return RESERVED_SUFFIXES.contains(last) || RESERVED_SUFFIXES.contains(host);
	}

	private boolean unsafeLiteralAddress(String host) {
		if (host.contains(":")) {
			return unsafeIpv6(host);
		}
		int[] octets = ipv4(host);
		if (octets == null) {
			// Block legacy numeric forms such as 127.1, 2130706433,
			// 0177.0.0.1 and 0x7f.0.0.1. Different clients may interpret them as
			// IP literals even though they are not canonical dotted decimal.
			return host.matches("(?i)(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+))*");
		}
		int first = octets[0];
		int second = octets[1];
		int third = octets[2];
		return first == 0 || first == 10 || first == 127 || first >= 224
				|| first == 100 && second >= 64 && second <= 127 || first == 169 && second == 254
				|| first == 172 && second >= 16 && second <= 31 || first == 192 && second == 168
				|| first == 192 && second == 0 || first == 192 && second == 0 && third == 2
				|| first == 198 && (second == 18 || second == 19) || first == 198 && second == 51 && third == 100
				|| first == 203 && second == 0 && third == 113;
	}

	private int[] ipv4(String host) {
		String[] parts = host.split("\\.", -1);
		if (parts.length != 4) {
			return null;
		}
		int[] result = new int[4];
		for (int index = 0; index < parts.length; index++) {
			if (!parts[index].matches("0|[1-9][0-9]{0,2}")) {
				return null;
			}
			int value = Integer.parseInt(parts[index]);
			if (value > 255) {
				return null;
			}
			result[index] = value;
		}
		return result;
	}

	private boolean unsafeIpv6(String host) {
		if (host.contains("%")) {
			return true;
		}
		try {
			InetAddress address = InetAddress.getByName(host);
			if (!(address instanceof Inet6Address)) {
				return true;
			}
			byte[] bytes = address.getAddress();
			boolean documentation = bytes[0] == 0x20 && bytes[1] == 0x01 && (bytes[2] & 0xff) == 0x0d
					&& (bytes[3] & 0xff) == 0xb8;
			boolean uniqueLocal = (bytes[0] & 0xfe) == 0xfc;
			return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
					|| address.isSiteLocalAddress() || address.isMulticastAddress() || documentation || uniqueLocal;
		}
		catch (Exception error) {
			return true;
		}
	}

	private void validateQueryKeys(String query, List<String> issues) {
		if (!StringUtils.hasText(query)) {
			return;
		}
		Set<String> keys = new HashSet<>();
		for (String pair : query.split("&", -1)) {
			String rawKey = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
			String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
			if (!StringUtils.hasText(key)) {
				issues.add("URL_QUERY_KEY_EMPTY");
			}
			else if (!keys.add(key)) {
				issues.add("URL_QUERY_KEY_DUPLICATE:" + key);
			}
		}
	}

	private LinkedHashSet<String> extractMacros(String source, List<String> issues, Set<String> allowedMacros) {
		LinkedHashSet<String> macros = new LinkedHashSet<>();
		Matcher matcher = MACRO.matcher(source);
		while (matcher.find()) {
			String macro = matcher.group().toUpperCase(Locale.ROOT);
			macros.add(macro);
			if (!allowedMacros.contains(macro)) {
				issues.add("URL_MACRO_NOT_ALLOWED:" + macro);
			}
		}
		return macros;
	}

	private String normalizedUrl(URI uri, String scheme, String host, int port) {
		int normalizedPort = "https".equals(scheme) && port == 443 || "http".equals(scheme) && port == 80 ? -1 : port;
		String displayHost = host.contains(":") ? "[" + host + "]" : host;
		String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
		return scheme + "://" + displayHost + (normalizedPort < 0 ? "" : ":" + normalizedPort) + path
				+ (StringUtils.hasText(uri.getRawQuery()) ? "?" + uri.getRawQuery() : "");
	}

	private String masked(String scheme, String host, int port, String query) {
		String displayHost = host.contains(":") ? "[" + host + "]" : host;
		return scheme + "://" + displayHost + (port < 0 ? "" : ":" + port) + "/…"
				+ (StringUtils.hasText(query) ? "?query=•••" : "");
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法计算 URL 指纹", error);
		}
	}

	private String lower(String value) {
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	public record Result(String normalizedUrl, String maskedUrl, String host, String fingerprint, List<String> macros,
			List<String> issues, String status) {
	}

}
