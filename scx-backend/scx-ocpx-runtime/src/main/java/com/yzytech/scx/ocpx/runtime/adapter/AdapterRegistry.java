package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AdapterRegistry {

	private final Map<String, UpstreamAdapter> upstream;

	private final Map<String, DownstreamAdapter> downstream;

	public AdapterRegistry(List<UpstreamAdapter> upstream, List<DownstreamAdapter> downstream) {
		this.upstream = upstream.stream()
			.collect(Collectors.toUnmodifiableMap(UpstreamAdapter::code, Function.identity()));
		this.downstream = downstream.stream()
			.collect(Collectors.toUnmodifiableMap(DownstreamAdapter::code, Function.identity()));
	}

	public UpstreamAdapter requireUpstream(String code) {
		UpstreamAdapter adapter = upstream.get(code);
		if (adapter == null) {
			throw new IllegalArgumentException("unknown upstream adapter: " + code);
		}
		return adapter;
	}

	public DownstreamAdapter requireDownstream(String code) {
		DownstreamAdapter adapter = downstream.get(code);
		if (adapter == null) {
			throw new IllegalArgumentException("unknown downstream adapter: " + code);
		}
		return adapter;
	}

}
