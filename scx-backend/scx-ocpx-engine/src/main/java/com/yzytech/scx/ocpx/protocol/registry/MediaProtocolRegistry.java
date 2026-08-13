package com.yzytech.scx.ocpx.protocol.registry;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 媒体主键到特殊下游协议的固定注册表。
 *
 * <p>只有小米等特殊媒体需要登记；未登记媒体按参考实现走通用 Callback URL 回传。</p>
 */
@Component
public class MediaProtocolRegistry {

	private static final Map<String, Protocol> REGISTRATIONS = Map.ofEntries();

	public Optional<Protocol> resolve(String mediaId) {
		return Optional.ofNullable(REGISTRATIONS.get(mediaId));
	}

	public enum Protocol {

		MI

	}

}
