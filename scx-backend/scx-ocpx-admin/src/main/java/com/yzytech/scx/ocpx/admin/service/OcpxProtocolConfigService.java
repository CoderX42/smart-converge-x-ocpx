package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.ProtocolConfigRequests;
import com.yzytech.scx.ocpx.admin.dto.ProtocolConfigViews;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProtocolAllowedHostMapper;
import com.yzytech.scx.ocpx.entity.OcpxProtocolAllowedHost;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import com.yzytech.scx.ocpx.protocol.ProtocolHostContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OcpxProtocolConfigService {

	private static final Set<OcpxUpstreamProtocol> HOST_CONFIGURABLE = Set.of(OcpxUpstreamProtocol.AERIM,
			OcpxUpstreamProtocol.WEIFENG, OcpxUpstreamProtocol.GRAVITY, OcpxUpstreamProtocol.RENREN_VIDEO,
			OcpxUpstreamProtocol.PHOENIX);

	private final OcpxProtocolAllowedHostMapper allowedHostMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final AdvertiserScope advertiserScope;

	public List<ProtocolConfigViews.Item> list() {
		advertiserScope.requireAdministrator();
		Map<OcpxUpstreamProtocol, List<String>> grouped = allowedHostMapper
			.selectList(Wrappers.<OcpxProtocolAllowedHost>lambdaQuery()
				.in(OcpxProtocolAllowedHost::getProtocolType, HOST_CONFIGURABLE)
				.orderByAsc(OcpxProtocolAllowedHost::getProtocolType, OcpxProtocolAllowedHost::getHost))
			.stream()
			.collect(Collectors.groupingBy(OcpxProtocolAllowedHost::getProtocolType,
					() -> new java.util.EnumMap<>(OcpxUpstreamProtocol.class),
					Collectors.mapping(OcpxProtocolAllowedHost::getHost, Collectors.toList())));
		return Arrays.stream(OcpxUpstreamProtocol.values())
			.filter(HOST_CONFIGURABLE::contains)
			.map(protocol -> new ProtocolConfigViews.Item(protocol, grouped.getOrDefault(protocol, List.of())))
			.toList();
	}

	@Transactional
	public ProtocolConfigViews.Item updateAllowedHosts(OcpxUpstreamProtocol protocol,
			ProtocolConfigRequests.UpdateAllowedHosts request) {
		advertiserScope.requireAdministrator();
		if (!HOST_CONFIGURABLE.contains(protocol)) {
			throw new BootBusinessException("该协议不支持域名白名单配置");
		}
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		try {
			request.getAllowedHosts()
				.forEach(host -> normalized.add(ProtocolHostContract.normalizeConfiguredHost(host)));
		}
		catch (IllegalArgumentException exception) {
			throw new BootBusinessException(exception.getMessage());
		}
		allowedHostMapper.delete(
				Wrappers.<OcpxProtocolAllowedHost>lambdaQuery().eq(OcpxProtocolAllowedHost::getProtocolType, protocol));
		normalized.forEach(host -> {
			OcpxProtocolAllowedHost value = new OcpxProtocolAllowedHost();
			value.setProtocolType(protocol);
			value.setHost(host);
			allowedHostMapper.insert(value);
		});
		configRefreshPublisher.mark();
		return new ProtocolConfigViews.Item(protocol, List.copyOf(normalized));
	}

}
