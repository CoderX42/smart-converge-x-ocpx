package com.yzytech.scx.ocpx.runtime.ingress;

import com.yzytech.scx.ocpx.contract.OcpxPartnerContext;
import com.yzytech.scx.ocpx.runtime.config.PartnerContextRepository;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Resolves /c/{taskCode} directly from the active monitor task and promotion link. */
@Service
public class PublicTrackingRouteService {

	private static final Pattern MACRO = Pattern.compile("__([A-Z0-9_]{2,64})__");

	private final PartnerContextRepository repository;

	public PublicTrackingRouteService(PartnerContextRepository repository) {
		this.repository = repository;
	}

	public Resolution resolve(String taskCode, MultiValueMap<String, String> query) {
		OcpxPartnerContext context = repository.findByTaskCode(taskCode)
			.orElseThrow(() -> new OcpxException(HttpStatus.GONE, "active monitor task is unavailable"));
		String destination = context.promotionUrlTemplate();
		if (destination == null || destination.isBlank()) {
			throw new OcpxException(HttpStatus.GONE, "promotion destination is unavailable");
		}
		for (Map.Entry<String, java.util.List<String>> item : query.entrySet()) {
			if (item.getValue().size() != 1 || item.getValue().get(0).length() > 2048) {
				throw bad("tracking parameter is invalid");
			}
			String token = "__" + item.getKey().trim().toUpperCase() + "__";
			if (!destination.contains(token)) {
				throw bad("unknown tracking parameter");
			}
			destination = destination.replace(token,
					UriUtils.encodeQueryParam(item.getValue().get(0), StandardCharsets.UTF_8));
		}
		if (MACRO.matcher(destination).find()) {
			throw bad("required tracking parameter is missing");
		}
		URI uri;
		try {
			uri = URI.create(destination);
		}
		catch (RuntimeException error) {
			throw bad("resolved destination is invalid");
		}
		if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
			throw bad("resolved destination is not a safe web URL");
		}
		return new Resolution(uri, context.configVersion());
	}

	private OcpxException bad(String message) {
		return new OcpxException(HttpStatus.BAD_REQUEST, message);
	}

	public record Resolution(URI destination, long configVersion) {
	}

}
