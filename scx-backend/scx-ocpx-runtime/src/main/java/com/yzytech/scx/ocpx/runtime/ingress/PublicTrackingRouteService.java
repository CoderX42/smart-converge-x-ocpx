package com.yzytech.scx.ocpx.runtime.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure routeCode/macro resolver. It never performs a network request. */
@Service
public class PublicTrackingRouteService {

	private static final Pattern UNRESOLVED_MACRO = Pattern.compile("__[A-Z0-9_]{2,64}__");

	private final RouteConfigurationCache routeCache;

	private final ObjectMapper objectMapper;

	public PublicTrackingRouteService(RouteConfigurationCache routeCache, ObjectMapper objectMapper) {
		this.routeCache = routeCache;
		this.objectMapper = objectMapper;
	}

	public Resolution resolve(String routeCode, MultiValueMap<String, String> query) {
		RouteConfigurationCache.PublicRoute configured = routeCache.requirePublicRoute(routeCode);
		if (configured.destinationTemplate() == null || configured.destinationTemplate().isBlank()
				|| configured.macroSchemaJson() == null || configured.macroSchemaJson().isBlank()) {
			throw new OcpxException(HttpStatus.GONE, "published route destination is unavailable");
		}
		String destination = configured.destinationTemplate();
		Set<String> allowed = new HashSet<>();
		try {
			JsonNode pairs = objectMapper.readTree(configured.macroSchemaJson()).path("pairs");
			for (JsonNode pair : pairs) {
				String key = pair.path("key").asText();
				String macro = pair.path("macro").asText();
				boolean required = pair.path("required").asBoolean(false);
				allowed.add(key);
				if (query.getOrDefault(key, java.util.List.of()).size() > 1) {
					throw badRequest("duplicate tracking macro");
				}
				String value = query.getFirst(key);
				if (required && (value == null || value.isBlank())) {
					throw badRequest("required tracking macro is missing");
				}
				if (value != null) {
					if (value.length() > 2048
							|| value.chars().anyMatch(character -> Character.isISOControl(character))) {
						throw badRequest("tracking macro value is invalid");
					}
					destination = destination.replace(macro, UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8));
				}
			}
		}
		catch (OcpxException error) {
			throw error;
		}
		catch (Exception error) {
			throw new OcpxException(HttpStatus.GONE, "published macro schema is unavailable");
		}
		if (query.keySet().stream().anyMatch(key -> !allowed.contains(key))) {
			throw badRequest("unknown tracking macro");
		}
		if (UNRESOLVED_MACRO.matcher(destination).find()) {
			throw badRequest("destination still contains unresolved macros");
		}
		URI uri;
		try {
			uri = URI.create(destination);
		}
		catch (RuntimeException error) {
			throw badRequest("resolved destination is invalid");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
		if (!Set.of("http", "https").contains(scheme) || uri.getUserInfo() != null || uri.getHost() == null) {
			throw badRequest("resolved destination is not a safe web URL");
		}
		return new Resolution(uri, configured.route().routeVersion());
	}

	private OcpxException badRequest(String message) {
		return new OcpxException(HttpStatus.BAD_REQUEST, message);
	}

	public record Resolution(URI destination, long routeVersion) {
	}

}
