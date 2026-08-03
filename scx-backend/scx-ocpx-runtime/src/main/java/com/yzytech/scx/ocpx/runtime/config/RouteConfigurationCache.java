package com.yzytech.scx.ocpx.runtime.config;

import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
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

	private volatile ConfigurationIndex index = new ConfigurationIndex(Map.of(), Map.of());

	public RouteConfigurationCache(RouteConfigurationRepository repository) {
		this.repository = repository;
	}

	public RouteSnapshot require(String channelCode, String routeReference) {
		String key = channelCode + ":" + routeReference;
		RouteSnapshot route = index.routes().get(key);
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
		}
		index = new ConfigurationIndex(Map.copyOf(next), Map.copyOf(channelAdapters));
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		reloadAll();
	}

	private record ConfigurationIndex(Map<String, RouteSnapshot> routes, Map<String, String> channelAdapters) {
	}

}
