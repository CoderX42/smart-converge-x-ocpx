package com.yzytech.scx.ocpx.configuration.index;

import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Engine 当前生效的协议域名白名单快照；未配置协议默认拒绝。 */
@Component
public class OcpxProtocolAllowedHostIndex {

	private volatile Map<OcpxUpstreamProtocol, Set<String>> snapshot = Map.of();

	public Set<String> get(OcpxUpstreamProtocol protocol) {
		return snapshot.getOrDefault(protocol, Set.of());
	}

	public void replace(Map<OcpxUpstreamProtocol, Set<String>> values) {
		Map<OcpxUpstreamProtocol, Set<String>> copy = new EnumMap<>(OcpxUpstreamProtocol.class);
		values
			.forEach((protocol, hosts) -> copy.put(protocol, Collections.unmodifiableSet(new LinkedHashSet<>(hosts))));
		snapshot = Collections.unmodifiableMap(copy);
	}

}
