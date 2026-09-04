package com.yzytech.scx.ocpx.configuration.index;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** RTA 运行配置的无密钥内存快照；原始设备信息和供应商认证信息不进入索引。 */
@Component
public class RtaConfigIndex {

	private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

	public void replace(Map<String, Snapshot> values) {
		snapshots.clear();
		snapshots.putAll(values);
	}

	public Snapshot get(String monitorTaskId) {
		return snapshots.get(monitorTaskId);
	}

	public record Snapshot(boolean enabled, String profileId, String adapterCode, String configJson,
			List<String> externalTaskIds) {
	}

}
