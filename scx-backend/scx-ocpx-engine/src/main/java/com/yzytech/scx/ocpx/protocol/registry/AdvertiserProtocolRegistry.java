package com.yzytech.scx.ocpx.protocol.registry;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 广告主主键到上游协议的固定注册表。
 *
 * <p>管理端创建广告主后，对接提交必须把生成的主键登记到这里。未登记主键不允许向上游转发。</p>
 */
@Component
public class AdvertiserProtocolRegistry {

	private static final Map<String, Protocol> REGISTRATIONS = Map.ofEntries();

	public Optional<Protocol> resolve(String advertiserId) {
		return Optional.ofNullable(REGISTRATIONS.get(advertiserId));
	}

	public enum Protocol {

		TEMPLATE_GET,

		KIMI

	}

}
