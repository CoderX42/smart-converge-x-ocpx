package com.yzytech.scx.ocpx.index;

import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserDspIndex implements IndexAware<String, OcpxAdvertiser> {

	private static final Map<String, OcpxAdvertiser> OBJECT_MAP = new ConcurrentHashMap<>();

	@Override
	public OcpxAdvertiser get(String key) {
		return OBJECT_MAP.get(key);
	}

	@Override
	public void add(String key, OcpxAdvertiser value) {
		OBJECT_MAP.put(key, value);
	}

	@Override
	public void clear() {
		OBJECT_MAP.clear();
	}

}
