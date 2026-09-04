package com.yzytech.scx.ocpx.rta.adapter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import com.yzytech.scx.ocpx.rta.model.RtaAdapterResult;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionRequest;
import com.yzytech.scx.ocpx.rta.model.RtaProfileSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;



/** 本地联调适配器，不执行任何外部网络请求。 */
@Component
public class RtaMockDecisionAdapter implements RtaDecisionAdapter {

	@Override
	public String code() {
		return "MOCK";
	}

	@Override
	public RtaAdapterResult decide(RtaDecisionRequest request, RtaProfileSnapshot profile,
			List<String> taskIds) {
		JSONObject config = parse(profile.configJson());
		long delayMs = Math.max(0, Math.min(10_000, config.getLongValue("delayMs")));
		if (delayMs > 0) {
			try {
				Thread.sleep(delayMs);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return new RtaAdapterResult(RtaDecision.DENY, "ERROR", "MOCK_ERROR", List.of());
			}
		}
		String decision = config.getString("decision");
		if ("ALLOW".equalsIgnoreCase(decision)) {
			List<String> configured = config.getList("taskIds", String.class);
			List<String> resultIds = configured == null || configured.isEmpty() ? taskIds
					: configured.stream().filter(taskIds::contains).distinct().toList();
			if (resultIds.isEmpty()) {
				return new RtaAdapterResult(RtaDecision.DENY, "REJECTED", "RTA_NO_MATCH", List.of());
			}
			return new RtaAdapterResult(RtaDecision.ALLOW, "SUCCESS", "MOCK_ALLOW", resultIds);
		}
		if ("ERROR".equalsIgnoreCase(decision)) {
			return new RtaAdapterResult(RtaDecision.DENY, "ERROR", "MOCK_ERROR", List.of());
		}
		return new RtaAdapterResult(RtaDecision.DENY, "SUCCESS", "MOCK_DENY", List.of());
	}

	private JSONObject parse(String value) {
		if (value == null || value.isBlank()) {
			return new JSONObject();
		}
		try {
			return JSON.parseObject(value);
		}
		catch (RuntimeException exception) {
			return new JSONObject();
		}
	}

}
