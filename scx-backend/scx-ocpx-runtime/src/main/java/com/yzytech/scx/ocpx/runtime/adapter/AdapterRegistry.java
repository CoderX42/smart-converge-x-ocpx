package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class AdapterRegistry {

	private final Map<String, UpstreamAdapter> upstream;

	private final Map<String, DownstreamAdapter> downstream;

	public AdapterRegistry(List<UpstreamAdapter> upstream, List<DownstreamAdapter> downstream) {
		this.upstream = registerUpstream(upstream);
		this.downstream = registerDownstream(downstream);
	}

	public UpstreamAdapter requireUpstream(String code) {
		String normalized = normalize(code);
		UpstreamAdapter adapter = upstream.get(normalized);
		if (adapter == null) {
			throw new AdapterNotFoundException("UPSTREAM", normalized);
		}
		return adapter;
	}

	public DownstreamAdapter requireDownstream(String code) {
		String normalized = normalize(code);
		DownstreamAdapter adapter = downstream.get(normalized);
		if (adapter == null) {
			throw new AdapterNotFoundException("DOWNSTREAM", normalized);
		}
		return adapter;
	}

	private Map<String, UpstreamAdapter> registerUpstream(List<UpstreamAdapter> adapters) {
		Map<String, UpstreamAdapter> result = new LinkedHashMap<>();
		for (UpstreamAdapter adapter : adapters) {
			if (!"UPSTREAM".equalsIgnoreCase(adapter.capabilities().direction())) {
				throw new IllegalStateException("upstream adapter declares wrong direction: " + adapter.code());
			}
			putUnique(result, normalize(adapter.code()), adapter);
		}
		return Map.copyOf(result);
	}

	private Map<String, DownstreamAdapter> registerDownstream(List<DownstreamAdapter> adapters) {
		Map<String, DownstreamAdapter> result = new LinkedHashMap<>();
		for (DownstreamAdapter adapter : adapters) {
			if (!"DOWNSTREAM".equalsIgnoreCase(adapter.capabilities().direction())) {
				throw new IllegalStateException("downstream adapter declares wrong direction: " + adapter.code());
			}
			putUnique(result, normalize(adapter.code()), adapter);
		}
		return Map.copyOf(result);
	}

	private <T> void putUnique(Map<String, T> adapters, String code, T adapter) {
		if (adapters.putIfAbsent(code, adapter) != null) {
			throw new IllegalStateException("duplicate OCPX adapter code: " + code);
		}
	}

	private String normalize(String code) {
		if (code == null || code.isBlank()) {
			throw new AdapterNotFoundException("OCPX", "<blank>");
		}
		return code.trim().toUpperCase(Locale.ROOT);
	}

}
