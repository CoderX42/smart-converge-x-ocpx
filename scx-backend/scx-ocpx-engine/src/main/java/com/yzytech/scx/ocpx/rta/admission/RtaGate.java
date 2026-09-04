package com.yzytech.scx.ocpx.rta.admission;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.rta.config.RtaProperties;
import com.yzytech.scx.ocpx.rta.exception.RtaAdmissionDeniedException;
import com.yzytech.scx.ocpx.rta.exception.RtaInfrastructureException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;



/** 在现有点击事实事务之前执行 RTA admission 强门禁。 */
@Component
public class RtaGate {

	private final JdbcTemplate jdbcTemplate;

	private final RtaAdmissionStore admissionStore;

	private final OcpxFlowLogWriter flowLogWriter;

	private final RtaProperties properties;

	private MeterRegistry meterRegistry;

	@Autowired
	public RtaGate(JdbcTemplate jdbcTemplate, RtaAdmissionStore admissionStore, OcpxFlowLogWriter flowLogWriter,
			RtaProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.admissionStore = admissionStore;
		this.flowLogWriter = flowLogWriter;
		this.properties = properties;
	}

	/** Compatibility constructor for existing embedders and unit tests. */
	public RtaGate(JdbcTemplate jdbcTemplate, RtaAdmissionStore admissionStore, OcpxFlowLogWriter flowLogWriter) {
		this(jdbcTemplate, admissionStore, flowLogWriter, enabledByDefaultForCompatibility());
	}

	@Autowired(required = false)
	void setMeterRegistry(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void requireAllowed(FeedbackReq request) {
		if (request == null || request.getEvent() == null || !"CLICK".equals(request.getEvent().name())) {
			return;
		}
		List<Boolean> enabled;
		try {
			enabled = jdbcTemplate.query("""
					SELECT rta_enabled FROM scx_ocpx_monitor_task
					WHERE id=? AND is_delete=0
					""", (rs, rowNum) -> rs.getBoolean(1), request.getTId());
		}
		catch (RuntimeException exception) {
			if (!properties.isEnabled() && missingRtaColumn(exception)) {
				// Keep legacy, pre-V128 instances serving their disabled RTA tasks while the
				// forward-only migration is pending.
				return;
			}
			throw new RtaInfrastructureException("RTA monitor task storage unavailable", exception);
		}
		if (enabled.size() != 1 || !Boolean.TRUE.equals(enabled.get(0))) {
			return;
		}
		try {
			admissionStore.requireAllowed(request);
		}
		catch (RtaAdmissionDeniedException exception) {
			counter("rta_gate_rejections_total", "reason", exception.getReasonCode());
			if (exception.getReasonCode().contains("MISSING") || exception.getReasonCode().contains("EXPIRED")) {
				counter("rta_admission_miss_total", "reason", exception.getReasonCode());
			}
			if (flowLogWriter != null) {
				flowLogWriter.rtaGate(request, false, exception.getReasonCode());
			}
			throw exception;
		}
		catch (RtaInfrastructureException exception) {
			counter("rta_gate_rejections_total", "reason", "RTA_INFRA_UNAVAILABLE");
			if (flowLogWriter != null) {
				flowLogWriter.rtaGate(request, false, "RTA_INFRA_UNAVAILABLE");
			}
			throw exception;
		}
		if (flowLogWriter != null) {
			flowLogWriter.rtaGate(request, true, "RTA_ADMISSION_ALLOWED");
		}
	}

	private void counter(String name, String tag, String value) {
		if (meterRegistry != null) {
			meterRegistry.counter(name, tag, value).increment();
		}
	}

	private static RtaProperties enabledByDefaultForCompatibility() {
		RtaProperties value = new RtaProperties();
		value.setEnabled(true);
		return value;
	}

	private boolean missingRtaColumn(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			String message = current.getMessage();
			if (message != null && message.toLowerCase().contains("rta_enabled")
					&& message.toLowerCase().contains("column")) {
				return true;
			}
		}
		return false;
	}

}
