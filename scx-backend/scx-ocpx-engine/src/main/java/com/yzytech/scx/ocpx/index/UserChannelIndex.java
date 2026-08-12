package com.yzytech.scx.ocpx.index;

import com.yzytech.scx.ocpx.entity.OcpxMedia;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserChannelIndex implements IndexAware<String, OcpxMedia> {

	private static final Map<String, OcpxMedia> OBJECT_MAP = new ConcurrentHashMap<>();

	@Override
	public OcpxMedia get(String key) {
		return OBJECT_MAP.get(key);
	}

	@Override
	public void add(String key, OcpxMedia value) {
		OBJECT_MAP.put(key, value);
	}

	@Override
	public void clear() {
		OBJECT_MAP.clear();
	}

}
