package com.yzytech.scx.ocpx.configuration.index;

import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 以监测任务 ID（请求字段 {@code tId}）为键的内存索引。 */
@Component
public class OcpxMonitorTaskIndex implements IndexAware<String, OcpxMonitorTask> {

	/** 静态并发 Map 使同一 JVM 内的索引访问共享同一份配置快照。 */
	private static final Map<String, OcpxMonitorTask> OBJECT_MAP = new ConcurrentHashMap<>();

	@Override
	public OcpxMonitorTask get(String key) {
		return OBJECT_MAP.get(key);
	}

	@Override
	public void add(String key, OcpxMonitorTask value) {
		OBJECT_MAP.put(key, value);
	}

	@Override
	public void clear() {
		OBJECT_MAP.clear();
	}

}
