package com.yzytech.scx.ocpx.oppo;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.feedback.service.FeedbackService;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.rta.relay.*;
import com.yzytech.scx.ocpx.rta.exception.RtaAdmissionDeniedException;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class OppoClickController {

	private final OppoRtaService oppo;

	private final RelayRepository repository;

	private final RelayStore store;

	private final FeedbackService feedback;

	private final OcpxFlowLogWriter logs;

	private final RelaySecurity security;

	public OppoClickController(OppoRtaService oppo, RelayRepository repository, RelayStore store,
			FeedbackService feedback, OcpxFlowLogWriter logs, RelaySecurity security) {
		this.oppo = oppo;
		this.repository = repository;
		this.store = store;
		this.feedback = feedback;
		this.logs = logs;
		this.security = security;
	}

	@GetMapping("/oppo/click/{code}/{strategy}")
	public ResponseEntity<?> click(@PathVariable String code, @PathVariable int strategy, HttpServletRequest http) {
		Map<String, String> q = new HashMap<>();
		Set<String> allowed = Set.of("request_id", "rta_request_id", "oaid", "ad_id", "ip", "ua", "ts", "model", "osv");
		http.getParameterMap().forEach((k, v) -> {
			if (!allowed.contains(k) || v.length != 1 || v[0].length() > 1024
					|| v[0].chars().anyMatch(Character::isISOControl))
				throw new RelayException(400, "OPPO_CLICK_INVALID");
			q.put(k, v[0]);
		});
		var media = oppo.media(code);
		String id = required(q, "request_id", 128), oaid = required(q, "oaid", 256), adId = required(q, "ad_id", 19);
		if (!adId.matches("[1-9][0-9]{0,18}"))
			throw new RelayException(400, "OPPO_AD_ID_INVALID");
		try {
			Long.parseLong(adId);
		}
		catch (NumberFormatException e) {
			throw new RelayException(400, "OPPO_AD_ID_INVALID");
		}
		String rtaId = q.containsKey("rta_request_id") ? required(q, "rta_request_id", 128) : id;
		String task = repository.taskByStrategy(media.mediaId(), strategy);
		var binding = repository.binding(task, media.mediaId());
		var state = store.find(media.mediaId(), OppoRtaService.internalId(rtaId, strategy));
		if (!"SYNC_RETURNED".equals(state.getDeliveryStatus())
				|| !RelaySecurity.hash(oaid).equals(state.getDeviceHash()) || !"ALLOW".equals(state.getStatus())
				|| state.getExpiresAt() <= System.currentTimeMillis() || !state.getMatchedTaskIds().contains(task))
			throw new RelayException(403, "OPPO_CLICK_NOT_ALLOWED");
		String model = required(q, "model", 128), timestamp = required(q, "ts", 13);
		if (!timestamp.matches("[0-9]{13}"))
			throw new RelayException(400, "OPPO_CLICK_TIMESTAMP_INVALID");
		FeedbackReq f = FeedbackReq.builder()
			.mId(binding.mediaId())
			.tId(task)
			.aId(binding.advertiserId())
			.lId(binding.linkId())
			.event(EventTypeEnum.CLICK)
			.reqId(id)
			.clickId(id)
			.creativeId(adId)
			.oaid(oaid)
			.oaidMd5(OppoRtaService.md5(oaid))
			.os(OsTypeEnum.ANDROID)
			.tsMs(timestamp)
			.model(model)
			.ip(optional(q, "ip"))
			.ua(optional(q, "ua"))
			.osv(optional(q, "osv"))
			.rtaRequestId(state.getRelayRequestId())
			.build();
		// This marker is retained inside the authenticated callback envelope and dispatch
		// copy.
		f.setDownstreamProtocol("OPPO");
		f.setOppoClickDevice(security.encrypt(state.getRelayRequestId() + ":oppo-click", oaid));
		f.setDownstreamCredentialRef(media.protocolConfig().get("uploadCredentialRef"));
		f.setOppoPkg(repository.oppoPkg(task));
		feedback.handleFeedback(f);
		logs.mediaIngress(f, true, "OPPO_CLICK_ACCEPTED");
		return ResponseEntity.ok(Map.of("ret", 0, "msg", ""));
	}

	private static String required(Map<String, String> q, String key, int max) {
		String value = q.get(key);
		if (value == null || value.isBlank() || value.length() > max || value.contains("__"))
			throw new RelayException(400, "OPPO_CLICK_" + key.toUpperCase(Locale.ROOT) + "_REQUIRED");
		return value;
	}

	private static String optional(Map<String, String> q, String key) {
		String v = q.get(key);
		return v == null || v.contains("__") || v.isBlank() ? null : v;
	}

	@ExceptionHandler(RelayException.class)
	public ResponseEntity<?> rejected(RelayException e) {
		return ResponseEntity.status(e.status()).body(Map.of("ret", e.status(), "msg", e.getMessage()));
	}

	@ExceptionHandler(RtaAdmissionDeniedException.class)
	public ResponseEntity<?> denied(RtaAdmissionDeniedException e) {
		return ResponseEntity.status(403).body(Map.of("ret", 403, "msg", "OPPO_CLICK_NOT_ALLOWED"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> failed(Exception e) {
		return ResponseEntity.status(503).body(Map.of("ret", 503, "msg", "OPPO_CLICK_UNAVAILABLE"));
	}

}
