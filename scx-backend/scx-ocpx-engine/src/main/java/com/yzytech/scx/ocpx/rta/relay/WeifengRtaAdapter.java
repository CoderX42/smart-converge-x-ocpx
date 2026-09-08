package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WeifengRtaAdapter implements RtaUpstreamAdapter {

	public String code() {
		return "WEIFENG_RTA";
	}

	public void validate(Request request, Upstream upstream) {
		RtaUpstreamAdapter.super.validate(request, upstream);
		if (!definition().fixedEndpoint().equals(upstream.endpoint()))
			throw new RelayException(400, "RTA_ENDPOINT_REJECTED");
		device(request);
	}

	public Outbound build(RtaState state, Request request, String callbackBase) {
		validate(request, state.getUpstream());
		var config = state.getUpstream().protocolConfig();
		String endpoint = state.getUpstream().endpoint() + "?aid=" + encode(config.get("aid")) + "&did="
				+ encode(config.get("did"));
		return new Outbound(endpoint, "",
				RelayJson.write(Map.of("id", state.getProviderRequestId(), "device", device(request))),
				Set.of("aid", "did"));
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private Map<String, String> device(Request request) {
		var result = new LinkedHashMap<String, String>();
		String os = Objects.toString(request.context().get("os"), "").toLowerCase(Locale.ROOT);
		if (!Set.of("ios", "android").contains(os))
			throw new RelayException(400, "WEIFENG_OS_INVALID");
		result.put("os", os);
		for (String[] f : List.of(new String[] { "idfa", "idfaMd5", "idfa_md5sum" },
				new String[] { "imei", "imeiMd5", "imei_md5sum" }, new String[] { "oaid", "oaidMd5", "oaid_md5sum" },
				new String[] { "androidId", "androidIdMd5", "android_id_md5sum" },
				new String[] { "", "macMd5", "mac_md5sum" })) {
			String hash = request.device().get(f[1]);
			if (hash != null && !hash.matches("[a-fA-F0-9]{32}"))
				throw new RelayException(400, "WEIFENG_MD5_INVALID");
			if (hash == null && request.device().containsKey(f[0]))
				hash = md5(request.device().get(f[0]));
			if (hash != null)
				result.put(f[2], hash.toLowerCase(Locale.ROOT));
		}
		for (String[] f : List.of(new String[] { "preCaid", "caid" }, new String[] { "preCaidVersion", "caid_version" },
				new String[] { "preCaidMd5", "caid_md5" }, new String[] { "caid", "caid_new" },
				new String[] { "caidVersion", "caid_new_version" }, new String[] { "caidMd5", "caid_new_md5" })) {
			String value = request.device().get(f[0]);
			if (value != null) {
				if (f[0].endsWith("Md5")) {
					if (!value.matches("[a-fA-F0-9]{32}"))
						throw new RelayException(400, "WEIFENG_MD5_INVALID");
					value = value.toLowerCase(Locale.ROOT);
				}
				result.put(f[1], value);
			}
		}
		for (String key : List.of("ip", "ipv6", "ua", "model"))
			if (request.context().containsKey(key))
				result.put(key, request.context().get(key));
		if (result.keySet().stream().noneMatch(k -> k.contains("md5") || k.equals("caid") || k.equals("caid_new")))
			throw new RelayException(400, "WEIFENG_DEVICE_REQUIRED");
		return result;
	}

	private String md5(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public Decision submitted(RtaState state, HttpResult response) {
		if (response.status() < 200 || response.status() >= 300)
			throw new RelayException(502, "WEIFENG_HTTP_ERROR");
		var body = RelayJson.read(response.body(), com.fasterxml.jackson.databind.JsonNode.class);
		if (!body.isObject() || body.size() != 2 || !body.path("id").isTextual()
				|| !body.path("code").isIntegralNumber() || !body.path("code").canConvertToInt()
				|| !state.getProviderRequestId().equals(body.path("id").textValue()))
			throw new RelayException(502, "WEIFENG_RESPONSE_INVALID");
		boolean allow = body.path("code").intValue() == 0;
		if (state.getBindings().size() != 1)
			throw new RelayException(400, "RTA_SINGLE_TASK_REQUIRED");
		var b = state.getBindings().get(0);
		return new Decision(state.getRelayRequestId(), allow ? "ALLOW" : "DENY",
				allow ? List.of(new Candidate(b.productId(), b.externalTaskId())) : List.of());
	}

}
