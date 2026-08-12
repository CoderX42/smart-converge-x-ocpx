package com.yzytech.scx.ocpx.index;

import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChannelMonitorIndex implements IndexAware<String, OcpxMonitorTask> {

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
