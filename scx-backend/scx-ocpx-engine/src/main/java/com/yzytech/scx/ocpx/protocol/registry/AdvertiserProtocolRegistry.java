package com.yzytech.scx.ocpx.protocol.registry;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 广告主主键到上游协议的数据库配置注册表。
 *
 * <p>
 * 管理端保存广告主后由配置刷新机制更新内存索引；未配置协议的历史广告主不允许向上游转发。
 * </p>
 */
@Component
public class AdvertiserProtocolRegistry {

	private final OcpxAdvertiserIndex advertiserIndex;

	public AdvertiserProtocolRegistry(OcpxAdvertiserIndex advertiserIndex) {
		this.advertiserIndex = advertiserIndex;
	}

	public Optional<Protocol> resolve(String advertiserId) {
		return Optional.ofNullable(advertiserIndex.get(advertiserId))
			.map(OcpxAdvertiser::getUpstreamProtocol)
			.map(this::map);
	}

	private Protocol map(OcpxUpstreamProtocol protocol) {
		return Protocol.valueOf(protocol.name());
	}

	public enum Protocol {

		TEMPLATE_GET,

		AERIM,

		WEIFENG,

		GRAVITY,

		RENREN_VIDEO

	}

}
