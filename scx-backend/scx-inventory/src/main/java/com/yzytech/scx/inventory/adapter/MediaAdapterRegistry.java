package com.yzytech.scx.inventory.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.inventory.entity.MediaAccount;
import com.yzytech.scx.inventory.entity.MediaAdapterConfig;
import com.yzytech.scx.inventory.mapper.MediaAccountMapper;
import com.yzytech.scx.inventory.mapper.MediaAdapterConfigMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MediaAdapterRegistry {

	private final Map<String, MediaAdapter> adapters = new ConcurrentHashMap<>();

	private final MediaAccountMapper accountMapper;

	private final MediaAdapterConfigMapper configMapper;

	public MediaAdapterRegistry(List<MediaAdapter> discoveredAdapters, MediaAccountMapper accountMapper,
			MediaAdapterConfigMapper configMapper) {
		this.accountMapper = accountMapper;
		this.configMapper = configMapper;
		discoveredAdapters.forEach(this::register);
	}

	public void register(MediaAdapter adapter) {
		adapters.put(adapter.adapterType(), adapter);
	}

	public MediaAdapter resolve(String mediaAccountId) {
		MediaAccount account = accountMapper.selectById(mediaAccountId);
		if (account == null)
			throw new IllegalArgumentException("媒体投放账户不存在: " + mediaAccountId);
		MediaAdapter adapter = adapters.get(account.getAdapterType());
		if (adapter == null)
			throw new IllegalStateException("未注册的适配器类型: " + account.getAdapterType());
		return adapter;
	}

	public MediaAdapterConfig getConfig(String mediaAccountId, String adapterType) {
		return configMapper.selectOne(Wrappers.<MediaAdapterConfig>lambdaQuery()
			.eq(MediaAdapterConfig::getMediaAccountId, mediaAccountId)
			.eq(MediaAdapterConfig::getAdapterType, adapterType));
	}

	public Map<String, MediaAdapter> all() {
		return Map.copyOf(adapters);
	}

}
