package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaState;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RelayService {

	private static final Set<String> DEVICE = Set.of("oaid", "oaidMd5", "imei", "imeiMd5", "idfa", "idfaMd5",
			"androidId", "androidIdMd5", "macMd5", "caid", "caidVersion", "caidMd5", "preCaid", "preCaidVersion",
			"preCaidMd5");

	private static final Set<String> CONTEXT = Set.of("os", "osv", "ip", "ipv6", "ua", "model");

	private final RelayRepository repository;

	private final RelayStore store;

	private final RelaySecurity security;

	private final RelayProperties properties;

	private final RelayAudit audit;

	private final RtaAdapterRegistry adapters;

	public RelayService(RelayRepository repository, RelayStore store, RelaySecurity security,
			RelayProperties properties, RelayAudit audit, RtaAdapterRegistry adapters) {
		this.adapters = adapters;
		this.repository = repository;
		this.store = store;
		this.security = security;
		this.properties = properties;
		this.audit = audit;
	}

	public Accepted accept(Request request, Media media) {
		return accept(request, media, Long.MAX_VALUE);
	}

	public Accepted accept(Request request, Media media, long deadline) {
		return accept(request, media, deadline, request.requestId());
	}

	public Accepted accept(Request request, Media media, long deadline, String originalRequestId) {
		if (!properties.isEnabled())
			throw new RelayException(503, "RTA_DISABLED");
		validate(request);
		Request canonical = new Request(request.requestId(), request.taskIds().stream().sorted().toList(),
				request.device(), request.context());
		String requestHash = RelaySecurity.hash(RelayJson.write(canonical));
		try {
			RtaState prior = store.find(media.mediaId(), request.requestId());
			if (!prior.getRequestHash().equals(requestHash))
				throw new RelayException(409, "RTA_REQUEST_CONFLICT");
			return new Accepted(prior.getRequestId(), prior.getRelayRequestId(), prior.getDeadline());
		}
		catch (RelayException e) {
			if (e.status() != 403)
				throw e;
		}
		List<Binding> bindings = request.taskIds()
			.stream()
			.sorted()
			.map(id -> repository.binding(id, media.mediaId()))
			.toList();
		String upstreamId = bindings.get(0).upstreamId();
		Upstream upstream = repository.upstream("id", upstreamId);
		Set<Candidate> tuples = new HashSet<>();
		for (Binding b : bindings) {
			if (!b.upstreamId().equals(upstreamId) || !b.advertiserId().equals(upstream.advertiserId()))
				throw new RelayException(400, "RTA_SPLIT_REQUIRED");
			if (!tuples.add(new Candidate(b.productId(), b.externalTaskId())))
				throw new RelayException(400, "RTA_BINDING_AMBIGUOUS");
		}
		var adapter = adapters.require(upstream.protocol());
		adapter.validate(canonical, upstream);
		if (adapter.definition().credentialRequired())
			security.secret(upstream.credentialRef());
		repository.validateRoute(upstream, bindings);

		RtaState state = new RtaState();
		state.setRequestId(request.requestId());
		state.setOriginalRequestId(originalRequestId);
		state.setRelayRequestId(UUID.randomUUID().toString().replace("-", ""));
		state.setProviderRequestId(state.getRelayRequestId());
		state.setRequestHash(requestHash);
		if ("OPPO".equals(media.protocol()))
			state.setDeviceHash(RelaySecurity.hash(canonical.device().getOrDefault("oaid", "")));
		state.setMediaId(media.mediaId());
		state.setAdvertiserId(upstream.advertiserId());
		state.setMedia(media);
		state.setUpstream(upstream);
		state.setBindings(bindings);
		state.setReceivedAt(System.currentTimeMillis());
		state.setDeadline(Math.min(deadline, state.getReceivedAt() + upstream.timeoutMs()));
		state.setEncryptedPayload(security.encrypt(state.getRelayRequestId(), RelayJson.write(canonical)));
		RtaState saved = store.init(state);
		if (saved.getRelayRequestId().equals(state.getRelayRequestId()))
			audit.write(saved, "MEDIA_ACCEPTED", true, "ACCEPTED", 0);
		return new Accepted(saved.getRequestId(), saved.getRelayRequestId(), saved.getDeadline());
	}

	public RtaState decide(Decision decision, String accessCode) {
		if (decision == null || decision.relayRequestId() == null || !decision.relayRequestId().matches("[a-f0-9]{32}")
				|| !Set.of("ALLOW", "DENY", "ERROR").contains(Objects.toString(decision.status(), ""))
				|| decision.matched() == null || decision.matched().size() > 100)
			throw new RelayException(400, "RTA_DECISION_INVALID");
		RtaState state = store.get(decision.relayRequestId());
		if (!state.getUpstream().accessCode().equals(accessCode))
			throw new RelayException(404, "RTA_REQUEST_NOT_FOUND");
		Set<Candidate> unique = new HashSet<>(decision.matched());
		if (unique.size() != decision.matched().size() || unique.contains(null))
			throw new RelayException(400, "RTA_MATCH_INVALID");
		List<String> matched = new ArrayList<>();
		for (Candidate c : unique) {
			var matches = state.getBindings()
				.stream()
				.filter(b -> Objects.equals(b.productId(), c.productId())
						&& Objects.equals(b.externalTaskId(), c.taskId()))
				.toList();
			if (matches.size() != 1) {
				if (state.getStatus().equals("PENDING")) {
					var failed = store.finish(state.getRelayRequestId(), "ERROR", "RTA_MATCH_INVALID", List.of(),
							"INVALID", System.currentTimeMillis());
					audit.write(failed, "UPSTREAM_DECISION", false, "RTA_MATCH_INVALID",
							failed.getCompletedAt() - failed.getReceivedAt());
				}
				throw new RelayException(400, "RTA_MATCH_INVALID");
			}
			matched.add(matches.get(0).taskId());
		}
		Collections.sort(matched);
		if (decision.status().equals("ALLOW") ? matched.isEmpty() : !matched.isEmpty())
			throw new RelayException(400, "RTA_MATCH_INVALID");
		String hash = RelaySecurity.hash(decision.status() + RelayJson.write(matched));
		RtaState result = store.finish(state.getRelayRequestId(), decision.status(), "UPSTREAM_" + decision.status(),
				matched, hash, System.currentTimeMillis());
		audit.write(result, "UPSTREAM_DECISION",
				!result.getStatus().equals("ERROR") && !result.getStatus().equals("TIMEOUT"), result.getReasonCode(),
				result.getCompletedAt() - result.getReceivedAt());
		return result;
	}

	public static void validate(Request r) {
		if (r == null || r.requestId() == null || !r.requestId().matches("[A-Za-z0-9._-]{1,128}") || r.taskIds() == null
				|| r.taskIds().isEmpty() || r.taskIds().size() > 100
				|| new HashSet<>(r.taskIds()).size() != r.taskIds().size()
				|| r.taskIds().stream().anyMatch(id -> id == null || !id.matches("[A-Za-z0-9_-]{1,32}")))
			throw new RelayException(400, "RTA_REQUEST_INVALID");
		fields(r.device(), DEVICE, 256);
		fields(r.context(), CONTEXT, 1024);
		if (r.device().isEmpty())
			throw new RelayException(400, "RTA_DEVICE_REQUIRED");
	}

	private static void fields(Map<String, String> values, Set<String> allowed, int limit) {
		if (values == null || !allowed.containsAll(values.keySet()) || values.values()
			.stream()
			.anyMatch(
					v -> v == null || v.isBlank() || v.length() > limit || v.chars().anyMatch(Character::isISOControl)))
			throw new RelayException(400, "RTA_FIELDS_INVALID");
	}

}
