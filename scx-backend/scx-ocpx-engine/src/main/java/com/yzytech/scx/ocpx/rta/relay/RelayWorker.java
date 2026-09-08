package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaState;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/** Subsecond runtime work only. SQL logging/aggregation belongs to XXL-JOB. */
@Component
public class RelayWorker {

	private final RelayStore store;

	private final RelaySecurity security;

	private final RelayProperties properties;

	private final RelayTransport transport;

	private final RelayRepository repository;

	private final RelayAudit audit;

	private final MeterRegistry metrics;

	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(16, 16, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(32), r -> {
				Thread t = new Thread(r, "rta-delivery");
				t.setDaemon(true);
				return t;
			}, new ThreadPoolExecutor.AbortPolicy());

	public RelayWorker(RelayStore store, RelaySecurity security, RelayProperties properties, RelayTransport transport,
			RelayRepository repository, RelayAudit audit, MeterRegistry metrics) {
		this.store = store;
		this.security = security;
		this.properties = properties;
		this.transport = transport;
		this.repository = repository;
		this.audit = audit;
		this.metrics = metrics;
	}

	@Scheduled(fixedDelay = 50, scheduler = "rtaScheduler")
	public void tick() {
		if (!properties.isEnabled())
			return;
		try {
			long now = System.currentTimeMillis();
			for (String id : store.claim("deadline", now, 100)) {
				try {
					RtaState s = store.finish(id, "TIMEOUT", "RTA_TIMEOUT", List.of(), "TIMEOUT", now);
					audit.write(s, "DECISION_TIMEOUT", false, "RTA_TIMEOUT", now - s.getReceivedAt());
				}
				catch (RelayException ignored) {
				}
			}
			int available = Math.max(0, 16 - executor.getActiveCount() - executor.getQueue().size());
			for (String queue : List.of("notify", "send")) {
				if (available == 0)
					break;
				List<String> ids = store.claim(queue, now, available);
				for (String id : ids)
					executor.execute(() -> run(queue, id));
				available -= ids.size();
			}
		}
		catch (RuntimeException e) {
			metrics.counter("rta_worker_failures_total").increment();
		}
	}

	public void run(String queue, String id) {
		try {
			if (queue.equals("send"))
				send(id);
			else
				notifyMedia(id);
		}
		catch (RuntimeException e) {
			metrics.counter("rta_delivery_worker_failures_total").increment();
		}
	}

	private void send(String id) {
		RtaState s = store.get(id);
		if (!s.getStatus().equals("PENDING"))
			return;
		long now = System.currentTimeMillis();
		if (now >= s.getDeadline()) {
			store.finish(id, "TIMEOUT", "RTA_TIMEOUT", List.of(), "TIMEOUT", now);
			return;
		}
		if (s.getEncryptedPayload() == null) {
			store.mutate("SUBMITTED", now, id);
			return;
		}
		try {
			for (var b : s.getBindings())
				repository.current(s, b);
			Request original = RelayJson.read(security.decrypt(id, s.getEncryptedPayload()), Request.class);
			String base = properties.getCallbackBaseUrl();
			URI callback = URI.create(base + "/rta/callbacks/" + s.getUpstream().accessCode());
			if (!"https".equals(callback.getScheme()) || callback.getHost() == null)
				throw new RelayException(503, "RTA_CALLBACK_BASE_UNAVAILABLE");
			var candidates = s.getBindings()
				.stream()
				.map(b -> new Candidate(b.productId(), b.externalTaskId()))
				.toList();
			String payload = RelayJson
				.write(Map.of("requestId", s.getRequestId(), "relayRequestId", id, "candidates", candidates, "device",
						original.device(), "context", original.context(), "callbackUrl", callback.toString()));
			long remaining = s.getDeadline() - System.currentTimeMillis();
			if (remaining <= 0) {
				store.finish(id, "TIMEOUT", "RTA_TIMEOUT", List.of(), "TIMEOUT", System.currentTimeMillis());
				return;
			}
			int status = transport.post(s.getUpstream().endpoint(), s.getUpstream().credentialRef(), payload,
					Math.min(1000, remaining));
			if (status == 202)
				store.mutate("SUBMITTED", System.currentTimeMillis(), id);
			else
				store.mutate("SEND_RETRY", System.currentTimeMillis(), id);
			audit.write(s, "UPSTREAM_SUBMIT", status == 202, "HTTP_" + status, System.currentTimeMillis() - now);
		}
		catch (RuntimeException e) {
			store.mutate("SEND_RETRY", System.currentTimeMillis(), id);
			audit.write(s, "UPSTREAM_SUBMIT", false, "SUBMIT_FAILED", System.currentTimeMillis() - now);
		}
	}

	private void notifyMedia(String id) {
		RtaState s = store.get(id);
		if (s.getStatus().equals("PENDING") || !s.getDeliveryStatus().equals("PENDING"))
			return;
		long now = System.currentTimeMillis();
		boolean success = false;
		String reason = "NOTIFY_DEADLINE";
		if (now < s.getDeliveryDeadline()) {
			try {
				Media current = repository.mediaByCredential(s.getMedia().credentialRef());
				if (!current.equals(s.getMedia()))
					throw new RelayException(403, "RTA_MEDIA_CHANGED");
				String payload = RelayJson.write(new Notification(s.getRequestId(), id, s.getStatus(),
						s.getMatchedTaskIds(), s.getReasonCode(), s.getExpiresAt()));
				int status = transport.post(s.getMedia().notifyUrl(), s.getMedia().credentialRef(), payload,
						Math.min(1000, s.getDeliveryDeadline() - now));
				success = status >= 200 && status < 300;
				reason = "HTTP_" + status;
			}
			catch (RuntimeException e) {
				reason = "NOTIFY_FAILED";
			}
		}
		store.mutate("DELIVERY", System.currentTimeMillis(), id, Boolean.toString(success));
		audit.write(s, "MEDIA_NOTIFY", success, reason, System.currentTimeMillis() - now);
	}

	@jakarta.annotation.PreDestroy
	public void close() {
		executor.shutdownNow();
	}

}
