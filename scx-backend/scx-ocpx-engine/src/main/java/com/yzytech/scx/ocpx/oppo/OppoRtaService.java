package com.yzytech.scx.ocpx.oppo;

import com.fasterxml.jackson.databind.JsonNode;
import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaState;
import com.yzytech.scx.ocpx.rta.contract.OppoMediaConfig;
import com.yzytech.scx.ocpx.rta.relay.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class OppoRtaService {

	private final RelayRepository repository;

	private final RelayService relay;

	private final RelayStore store;

	private final RelaySecurity security;

	private final RelayAudit audit;

	public OppoRtaService(RelayRepository repository, RelayService relay, RelayStore store, RelaySecurity security,
			RelayAudit audit) {
		this.repository = repository;
		this.relay = relay;
		this.store = store;
		this.security = security;
		this.audit = audit;
	}

	public Media media(String code) {
		Media m = repository.mediaByCredential(code);
		if (!"OPPO".equals(m.protocol()))
			throw new RelayException(403, "OPPO_MEDIA_UNAVAILABLE");
		return m;
	}

	public record Input(String requestId, String oaid, String ip, long time, List<Integer> strategies, String sign,
			String owner) {
	}

	public record Data(String id, List<Integer> agree_type_list) {
	}

	public record Response(int ret, String msg, Data data) {
	}

	public Input parse(JsonNode n, Media media, long now) {
		if (!n.isObject())
			throw new RelayException(400, "OPPO_REQUEST_INVALID");
		Set<String> fields = Set.of("request_id", "oaid", "app_id", "sex", "age", "city", "client_ip", "owner_id",
				"request_time", "channel_name", "ad_type_list", "interest_tag_map", "app_info_list", "sign");
		n.fieldNames().forEachRemaining(k -> {
			if (!fields.contains(k))
				throw new RelayException(400, "OPPO_UNKNOWN_FIELD");
		});
		for (String key : List.of("app_id", "sex", "age"))
			if (n.hasNonNull(key) && (!n.get(key).isIntegralNumber() || !n.get(key).canConvertToInt()))
				throw new RelayException(400, "OPPO_FIELD_INVALID");
		text(n, "city", 1024, false);
		if (n.hasNonNull("interest_tag_map")) {
			JsonNode tags = n.get("interest_tag_map");
			if (!tags.isObject() || tags.size() > 256)
				throw new RelayException(400, "OPPO_FIELD_INVALID");
			for (JsonNode value : tags)
				if (!value.isIntegralNumber() || !value.canConvertToLong())
					throw new RelayException(400, "OPPO_FIELD_INVALID");
		}
		if (n.hasNonNull("app_info_list")) {
			JsonNode apps = n.get("app_info_list");
			if (!apps.isArray() || apps.size() > 100)
				throw new RelayException(400, "OPPO_FIELD_INVALID");
			for (JsonNode app : apps) {
				if (!app.isObject() || app.size() != 2 || !app.path("app_status").isIntegralNumber()
						|| !app.path("app_status").canConvertToInt()
						|| !Set.of(0, 1).contains(app.path("app_status").intValue()))
					throw new RelayException(400, "OPPO_FIELD_INVALID");
				text(app, "app_name", 256, true);
			}
		}
		String id = text(n, "request_id", 128, true), oaid = text(n, "oaid", 256, false),
				ip = text(n, "client_ip", 128, false);
		if (!id.matches("[A-Za-z0-9._-]{1,128}") || !"OPPO".equals(text(n, "channel_name", 16, true)))
			throw new RelayException(400, "OPPO_REQUEST_INVALID");
		if (!n.path("request_time").isIntegralNumber() || !n.path("request_time").canConvertToLong())
			throw new RelayException(400, "OPPO_TIME_INVALID");
		long time = n.path("request_time").longValue();
		if (time < now - 300000 || time > now + 300000)
			throw new RelayException(401, "OPPO_SIGNATURE_EXPIRED");
		var config = OppoMediaConfig.validate(media.protocolConfig());
		String owner = "";
		JsonNode ownerNode = n.get("owner_id");
		if (config.get("ownerMode").equals("VALUE")) {
			if (ownerNode == null || !ownerNode.isIntegralNumber() || !ownerNode.canConvertToLong())
				throw new RelayException(401, "OPPO_OWNER_INVALID");
			owner = ownerNode.asText();
			if (!owner.equals(config.get("ownerId")))
				throw new RelayException(401, "OPPO_OWNER_INVALID");
		}
		else if (ownerNode != null && !ownerNode.isNull())
			throw new RelayException(401, "OPPO_OWNER_INVALID");
		String signature = text(n, "sign", 32, true);
		if (!signature.matches("[a-fA-F0-9]{32}"))
			throw new RelayException(401, "OPPO_SIGNATURE_INVALID");
		String expected = md5(id + owner + "OPPO" + time + security.secret(media.credentialRef()));
		if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
				signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
			throw new RelayException(401, "OPPO_SIGNATURE_INVALID");
		JsonNode list = n.path("ad_type_list");
		if (!list.isArray() || list.isEmpty() || list.size() > 100)
			throw new RelayException(400, "OPPO_STRATEGIES_INVALID");
		List<Integer> strategies = new ArrayList<>();
		for (JsonNode s : list) {
			if (!s.isIntegralNumber() || !s.canConvertToInt() || s.intValue() <= 0 || strategies.contains(s.intValue()))
				throw new RelayException(400, "OPPO_STRATEGIES_INVALID");
			strategies.add(s.intValue());
		}
		if (oaid.startsWith("__") || oaid.chars().anyMatch(Character::isISOControl))
			throw new RelayException(400, "OPPO_DEVICE_INVALID");
		return new Input(id, oaid, ip, time, List.copyOf(strategies), signature, owner);
	}

	public Response decide(Media media, Input input, long receivedAt) throws InterruptedException {
		long deadline = receivedAt
				+ Integer.parseInt(OppoMediaConfig.validate(media.protocolConfig()).get("timeoutMs"));
		// One parent fingerprint prevents retrying a request ID with a different strategy
		// set/device.
		store.fingerprint(media.mediaId(), input.requestId(), RelaySecurity.hash(RelayJson
			.write(List.of(input.strategies().stream().sorted().toList(), input.oaid(), input.ip(), input.owner()))));
		Map<Integer, Accepted> accepted = new LinkedHashMap<>();
		boolean error = false;
		for (int strategy : input.strategies()) {
			if (System.currentTimeMillis() >= deadline) {
				error = true;
				break;
			}
			try {
				String task = repository.taskByStrategy(media.mediaId(), strategy);
				Binding binding = repository.binding(task, media.mediaId());
				if (!"WEIFENG_RTA".equals(repository.upstream("id", binding.upstreamId()).protocol()))
					throw new RelayException(400, "OPPO_UPSTREAM_UNSUPPORTED");
				// No OAID means no supported identifier for the current Weifeng adapter.
				if (input.oaid().isBlank())
					continue;
				Map<String, String> context = new HashMap<>();
				context.put("os", "android");
				if (!input.ip().isBlank())
					context.put("ip", input.ip());
				accepted
					.put(strategy,
							relay.accept(
									new Request(internalId(input.requestId(), strategy), List.of(task),
											Map.of("oaid", input.oaid()), context),
									media, deadline, input.requestId()));
			}
			catch (RelayException e) {
				if (e.status() == 409)
					throw e;
				error = true;
			}
		}
		Map<Integer, RtaState> done = new LinkedHashMap<>();
		while (done.size() < accepted.size()) {
			for (var entry : accepted.entrySet()) {
				if (done.containsKey(entry.getKey()))
					continue;
				var state = store.get(entry.getValue().relayRequestId());
				if (!state.getStatus().equals("PENDING"))
					done.put(entry.getKey(), state);
			}
			if (done.size() == accepted.size() || System.currentTimeMillis() >= deadline)
				break;
			Thread.sleep(Math.min(10, Math.max(1, deadline - System.currentTimeMillis())));
		}
		List<Integer> allowed = new ArrayList<>();
		for (var entry : done.entrySet()) {
			RtaState state = entry.getValue();
			if (state.getStatus().equals("ALLOW") && state.getExpiresAt() > System.currentTimeMillis()) {
				repository.current(state, state.getBindings().get(0));
				store.mutate("SYNC_RESPONSE", System.currentTimeMillis(), state.getRelayRequestId());
				allowed.add(entry.getKey());
			}
			else if (!state.getStatus().equals("DENY"))
				error = true;
			audit.write(state, "OPPO_SYNC_RESPONSE",
					state.getStatus().equals("ALLOW") || state.getStatus().equals("DENY"), state.getReasonCode(),
					System.currentTimeMillis() - receivedAt);
		}
		error |= done.size() < accepted.size();
		return new Response(error && allowed.isEmpty() ? 1 : 0, error ? "PARTIAL_OR_UNAVAILABLE" : "",
				new Data(input.requestId(), allowed));
	}

	public static String internalId(String id, int strategy) {
		return "oppo." + RelaySecurity.hash(id + ":" + strategy);
	}

	public static String md5(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String text(JsonNode n, String key, int max, boolean required) {
		var v = n.get(key);
		if (v == null || v.isNull()) {
			if (required)
				throw new RelayException(400, "OPPO_FIELD_INVALID");
			return "";
		}
		if (!v.isTextual() || v.textValue().length() > max || required && v.textValue().isBlank()
				|| v.textValue().chars().anyMatch(Character::isISOControl))
			throw new RelayException(400, "OPPO_FIELD_INVALID");
		return v.textValue();
	}

}
