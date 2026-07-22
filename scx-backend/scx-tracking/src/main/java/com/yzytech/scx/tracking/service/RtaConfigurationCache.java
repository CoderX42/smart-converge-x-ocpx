package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.RtaEndpointMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RtaConfigurationCache {

	private static final long TTL_NANOS = Duration.ofSeconds(60).toNanos();

	private final TrackingTaskMapper taskMapper;

	private final RtaEndpointMapper endpointMapper;

	private final ConcurrentHashMap<String, CachedConfiguration> cache = new ConcurrentHashMap<>();

	public Configuration resolve(String taskCode) {
		long now = System.nanoTime();
		CachedConfiguration cached = cache.get(taskCode);
		if (cached != null && cached.expiresAtNanos() > now)
			return cached.configuration();
		TrackingTask task = taskMapper
			.selectOne(Wrappers.<TrackingTask>lambdaQuery().eq(TrackingTask::getCode, taskCode).last("LIMIT 1"));
		if (task == null || !"ACTIVE".equals(task.getStatus()) || !Boolean.TRUE.equals(task.getRtaEnabled()))
			throw new IllegalArgumentException("任务未启用 RTA 决策");
		RtaEndpoint endpoint = endpointMapper.selectById(task.getRtaEndpointId());
		if (endpoint == null || !task.getMediaId().equals(endpoint.getMediaId())
				|| !"ACTIVE".equals(endpoint.getStatus()))
			throw new IllegalArgumentException("任务绑定的 RTA 配置不可用");
		Configuration configuration = new Configuration(task, endpoint);
		cache.put(taskCode, new CachedConfiguration(configuration, now + TTL_NANOS));
		return configuration;
	}

	public record Configuration(TrackingTask task, RtaEndpoint endpoint) {
	}

	private record CachedConfiguration(Configuration configuration, long expiresAtNanos) {
	}

}
