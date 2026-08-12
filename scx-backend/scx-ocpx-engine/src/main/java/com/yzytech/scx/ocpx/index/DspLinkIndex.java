package com.yzytech.scx.ocpx.index;

import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DspLinkIndex implements IndexAware<String, OcpxPromotionLink> {

	private static final Map<String, OcpxPromotionLink> OBJECT_MAP = new ConcurrentHashMap<>();

	@Override
	public OcpxPromotionLink get(String key) {
		return OBJECT_MAP.get(key);
	}

	@Override
	public void add(String key, OcpxPromotionLink value) {
		OBJECT_MAP.put(key, value);
	}

	@Override
	public void clear() {
		OBJECT_MAP.clear();
	}

}
