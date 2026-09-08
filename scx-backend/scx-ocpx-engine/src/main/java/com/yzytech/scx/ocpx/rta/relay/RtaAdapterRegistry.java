package com.yzytech.scx.ocpx.rta.relay;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RtaAdapterRegistry {

	private final Map<String, RtaUpstreamAdapter> adapters = new HashMap<>();

	public RtaAdapterRegistry(List<RtaUpstreamAdapter> installed) {
		for (var adapter : installed) {
			adapter.definition();
			if (adapters.putIfAbsent(adapter.code(), adapter) != null)
				throw new IllegalStateException("Duplicate RTA protocol");
		}
	}

	public RtaUpstreamAdapter require(String code) {
		var adapter = adapters.get(code);
		if (adapter == null)
			throw new RelayException(400, "RTA_PROTOCOL_UNAVAILABLE");
		return adapter;
	}

}
