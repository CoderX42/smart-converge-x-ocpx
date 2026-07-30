package com.yzytech.scx.inventory.service;

import com.yzytech.scx.inventory.adapter.GenericSandboxMediaAdapter;
import com.yzytech.scx.inventory.adapter.MediaAdapter;
import com.yzytech.scx.inventory.adapter.MediaAdapterRegistry;
import com.yzytech.scx.inventory.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class MediaAdapterService {

	private final MediaAdapterRegistry registry;

	private final InventoryService inventoryService;

	public CapabilityView getCapabilities(String mediaAccountId) {
		inventoryService.requireMediaAccount(mediaAccountId);
		return registry.resolve(mediaAccountId).getCapabilities();
	}

	public PublishResult publishCampaign(String mediaAccountId, PublishRequest request) {
		inventoryService.requireWritableMediaAccount(mediaAccountId);
		if (request == null || request.campaignId() == null || request.campaignId().isBlank()
				|| request.placementId() == null || request.placementId().isBlank() || request.idempotencyKey() == null
				|| request.idempotencyKey().isBlank())
			throw new IllegalArgumentException("发布请求不完整");
		return registry.resolve(mediaAccountId).publishCampaign(request);
	}

	public PublishResult changeStatus(String mediaAccountId, String externalId, String newStatus, String key) {
		inventoryService.requireWritableMediaAccount(mediaAccountId);
		if (key == null || key.isBlank())
			throw new IllegalArgumentException("幂等键不能为空");
		return registry.resolve(mediaAccountId).changeStatus(externalId, newStatus, key);
	}

	public StatusQueryResult queryStatus(String mediaAccountId, String externalId) {
		inventoryService.requireMediaAccount(mediaAccountId);
		return registry.resolve(mediaAccountId).queryStatus(externalId);
	}

	public ReportPullResult pullReport(String mediaAccountId, String start, String end, String cursor) {
		inventoryService.requireMediaAccount(mediaAccountId);
		return registry.resolve(mediaAccountId).pullReport(mediaAccountId, start, end, cursor);
	}

	public ReportPullResult pullBilling(String mediaAccountId, String start, String end, String cursor) {
		inventoryService.requireMediaAccount(mediaAccountId);
		return registry.resolve(mediaAccountId).pullBilling(mediaAccountId, start, end, cursor);
	}

	public boolean isHealthy(String mediaAccountId) {
		inventoryService.requireMediaAccount(mediaAccountId);
		return registry.resolve(mediaAccountId).isHealthy();
	}

	/** Inject fault for sandbox testing only */
	public void injectSandboxFault(String fault, int value) {
		if (!Set.of("rate", "timeout", "down", "reset").contains(fault))
			throw new IllegalArgumentException("不支持的故障类型");
		MediaAdapter adapter = registry.all().get("GENERIC_SANDBOX");
		if (adapter instanceof GenericSandboxMediaAdapter sandbox)
			sandbox.injectFault(fault, value);
		else
			throw new IllegalStateException("Fault injection only available for GENERIC_SANDBOX adapter");
	}

	public void putSandboxReport(String syncType, String mediaAccountId, String start, String end, String cursor,
			ReportPullResult result) {
		inventoryService.requireWritableMediaAccount(mediaAccountId);
		if (!Set.of("PERFORMANCE", "BILLING").contains(syncType))
			throw new IllegalArgumentException("同步类型必须为 PERFORMANCE 或 BILLING");
		MediaAdapter adapter = registry.resolve(mediaAccountId);
		if (adapter instanceof GenericSandboxMediaAdapter sandbox)
			sandbox.putReport(syncType, mediaAccountId, start, end, cursor, result);
		else
			throw new IllegalArgumentException("该媒体账户不是沙箱适配器");
	}

}
