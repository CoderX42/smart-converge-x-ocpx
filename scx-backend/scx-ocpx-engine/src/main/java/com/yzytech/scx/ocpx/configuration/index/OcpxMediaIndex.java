package com.yzytech.scx.ocpx.configuration.index;

import com.yzytech.scx.ocpx.entity.OcpxMedia;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 以媒体 ID（请求字段 {@code mId}）为键的内存索引。 */
@Component
public class OcpxMediaIndex implements IndexAware<String, OcpxMedia> {

	/** 静态并发 Map 使同一 JVM 内的索引访问共享同一份配置快照。 */
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
