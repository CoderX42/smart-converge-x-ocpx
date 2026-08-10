package com.yzytech.scx.ocpx.runtime.config;

import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;

@Component
public class RouteConfigurationCache implements MessageListener {

	public static final String INVALIDATION_TOPIC = "scx:ocpx:config:changed";

	private final RouteConfigurationRepository repository;

	private final ProtocolCrypto protocolCrypto;

	private volatile ConfigurationIndex index = new ConfigurationIndex(Map.of(), Map.of(), Map.of());

	public RouteConfigurationCache(RouteConfigurationRepository repository, ProtocolCrypto protocolCrypto) {
		this.repository = repository;
		this.protocolCrypto = protocolCrypto;
	}

	public RouteSnapshot require(String channelCode, String routeReference) {
		String key = channelCode + ":" + routeReference;
		RouteSnapshot route = index.routes().get(key);
		if (route == null) {
			throw new OcpxException(HttpStatus.NOT_FOUND, "published route not found");
		}
		return route;
	}

	public RouteSnapshot requireByRouteCode(String routeCode) {
		return requirePublicRoute(routeCode).route();
	}

	public PublicRoute requirePublicRoute(String routeCode) {
		if (routeCode == null || !routeCode.matches("^[A-Za-z0-9_-]{40,64}$")) {
			throw new OcpxException(HttpStatus.NOT_FOUND, "published route not found");
		}
		String hash = protocolCrypto.blindIndex("ADX_ROUTE_CODE", routeCode);
		if (hash == null || hash.isBlank()) {
			throw new OcpxException(HttpStatus.NOT_FOUND, "published route not found");
		}
		PublicRoute route = index.routeCodes().get(hash);
		if (route == null) {
			throw new OcpxException(HttpStatus.NOT_FOUND, "published route not found");
		}
		return route;
	}

	public String requireDownstreamAdapter(String channelCode) {
		String adapterCode = index.channelAdapters().get(channelCode);
		if (adapterCode == null) {
			throw new OcpxException(HttpStatus.NOT_FOUND, "published channel route not found");
		}
		return adapterCode;
	}

	@PostConstruct
	public void loadAtStartup() {
		reloadAll();
	}

	@Scheduled(fixedDelay = 60000)
	public void reloadAll() {
		Map<String, RouteSnapshot> next = new HashMap<>();
		Map<String, String> channelAdapters = new HashMap<>();
		Map<String, PublicRoute> routeCodes = new HashMap<>();
		for (RouteConfigurationRepository.PublishedRoute configured : repository.findAllPublished()) {
			RouteSnapshot route = configured.route();
			String existing = channelAdapters.putIfAbsent(route.channelCode(), route.channelAdapterCode());
			if (existing != null && !existing.equals(route.channelAdapterCode())) {
				throw new IllegalStateException("published channel routes use inconsistent downstream adapters");
			}
			next.put(route.channelCode() + ":" + route.routeId(), route);
			if (configured.bizId() != null && !configured.bizId().isBlank()) {
				next.put(route.channelCode() + ":" + configured.bizId(), route);
			}
			if (configured.routeCodeHash() != null && !configured.routeCodeHash().isBlank()) {
				routeCodes.put(configured.routeCodeHash(),
						new PublicRoute(route, configured.destinationTemplate(), configured.macroSchemaJson()));
			}
		}
		index = new ConfigurationIndex(Map.copyOf(next), Map.copyOf(channelAdapters), Map.copyOf(routeCodes));
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		reloadAll();
	}

	private record ConfigurationIndex(Map<String, RouteSnapshot> routes, Map<String, String> channelAdapters,
			Map<String, PublicRoute> routeCodes) {
	}

	public record PublicRoute(RouteSnapshot route, String destinationTemplate, String macroSchemaJson) {
	}

}
