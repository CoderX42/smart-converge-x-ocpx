package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.DownstreamDeliveryContext;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
public class TemplateGetDownstreamAdapter implements DownstreamAdapter {

	@Override
	public String code() {
		return "TEMPLATE_GET_DOWNSTREAM";
	}

	@Override
	public AdapterCapabilities capabilities() {
		return new AdapterCapabilities("DOWNSTREAM", Set.of(OcpxEventType.values()), Set.of("GET"),
				Set.of("NONE", "TOKEN", "SIGNATURE"), Duration.ofSeconds(3),
				Set.of("__EVENT__", "__EVENT_ID__", "__CLICKID__", "__MONITOR_TASK_ID__"), true);
	}

	@Override
	public AdapterHttpRequest buildCallback(DownstreamDeliveryContext context) {
		return AdapterRequestSupport.downstreamTemplateGet(context);
	}

	@Override
	public AdapterDeliveryResult evaluateCallbackResponse(AdapterHttpResponse response) {
		return AdapterRequestSupport.http2xx(response, "DOWNSTREAM");
	}

}
