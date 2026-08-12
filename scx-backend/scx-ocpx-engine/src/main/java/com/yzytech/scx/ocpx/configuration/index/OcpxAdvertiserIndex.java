package com.yzytech.scx.ocpx.configuration.index;

import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 以上游广告主 ID（请求字段 {@code aId}）为键的内存索引。 */
@Component
public class OcpxAdvertiserIndex implements IndexAware<String, OcpxAdvertiser> {

	/** 静态并发 Map 使同一 JVM 内的索引访问共享同一份配置快照。 */
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
