package com.yzytech.scx.inventory.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.delivery.entity.MediaChannel;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.inventory.entity.*;
import com.yzytech.scx.inventory.mapper.*;
import com.yzytech.scx.subject.service.MediaScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InventoryService {

	private final MediaAccountMapper mediaAccountMapper;

	private final PlacementMapper placementMapper;

	private final MediaCapabilityMapper capabilityMapper;

	private final PlacementQualitySnapshotMapper qualityMapper;

	private final MediaAdapterConfigMapper adapterMapper;

	private final MediaChannelMapper channelMapper;

	private final MediaScopeService scopeService;

	public MediaAccount requireMediaAccount(String id) {
		MediaAccount a = mediaAccountMapper.selectById(id);
		if (a == null || !scopeService.canRead(a.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		return a;
	}

	public MediaAccount requireWritableMediaAccount(String id) {
		MediaAccount account = requireMediaAccount(id);
		if (!scopeService.canWrite(account.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		return account;
	}

	@Transactional
	public MediaAccount createMediaAccount(String mediaId, String channelId, String accountName, String adapterType,
			String accountIdExternal) {
		if (!scopeService.canWrite(mediaId))
			throw new IllegalArgumentException("资源不存在");
		if (!StringUtils.hasText(accountName))
			throw new IllegalArgumentException("媒体投放账户名称不能为空");
		requireMediaChannel(mediaId, channelId, true);
		MediaAccount a = new MediaAccount();
		a.setMediaId(mediaId);
		a.setChannelId(channelId);
		a.setAccountName(accountName.trim());
		a.setAdapterType(StringUtils.hasText(adapterType) ? adapterType : "GENERIC_SANDBOX");
		a.setAccountIdExternal(accountIdExternal);
		a.setStatus("ACTIVE");
		a.setCurrency("CNY");
		a.setTimezone("Asia/Shanghai");
		mediaAccountMapper.insert(a);
		return a;
	}

	public Page<MediaAccount> listMediaAccounts(Page<MediaAccount> page, String mediaId, String channelId,
			String keyword) {
		if (!scopeService.canRead(mediaId))
			throw new IllegalArgumentException("资源不存在");
		if (StringUtils.hasText(channelId))
			requireMediaChannel(mediaId, channelId, false);
		var q = Wrappers.<MediaAccount>lambdaQuery()
			.eq(MediaAccount::getMediaId, mediaId)
			.eq(StringUtils.hasText(channelId), MediaAccount::getChannelId, channelId)
			.and(StringUtils.hasText(keyword), x -> x.like(MediaAccount::getAccountName, keyword));
		return mediaAccountMapper.selectPage(page, q.orderByDesc(MediaAccount::getCreateTime));
	}

	@Transactional
	public MediaAccount updateMediaAccount(String id, String channelId, String accountName, String accountIdExternal,
			String adapterType) {
		MediaAccount account = requireWritableMediaAccount(id);
		if (StringUtils.hasText(channelId)) {
			requireMediaChannel(account.getMediaId(), channelId, true);
			account.setChannelId(channelId);
		}
		if (StringUtils.hasText(accountName))
			account.setAccountName(accountName.trim());
		if (accountIdExternal != null)
			account.setAccountIdExternal(accountIdExternal);
		if (StringUtils.hasText(adapterType))
			account.setAdapterType(adapterType);
		mediaAccountMapper.updateById(account);
		return account;
	}

	@Transactional
	public MediaAccount updateMediaAccountStatus(String id, String status) {
		if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status))
			throw new IllegalArgumentException("媒体投放账户状态不合法");
		MediaAccount account = requireWritableMediaAccount(id);
		account.setStatus(status);
		mediaAccountMapper.updateById(account);
		return account;
	}

	@Transactional
	public boolean deleteMediaAccount(String id) {
		MediaAccount account = requireWritableMediaAccount(id);
		if (mediaAccountMapper.countReferences(id) > 0)
			throw new IllegalStateException("该媒体投放账户仍被广告位、适配器配置或 Campaign 发布记录引用，请先解除引用或将账户设为停用");
		return mediaAccountMapper.deleteById(account.getId()) == 1;
	}

	@Transactional
	public Placement createPlacement(String mediaAccountId, String code, String name, String type, String creatives) {
		requireWritableMediaAccount(mediaAccountId);
		if (!StringUtils.hasText(code) || !StringUtils.hasText(name) || !StringUtils.hasText(type))
			throw new IllegalArgumentException("广告位编码、名称和类型不能为空");
		if (placementMapper.selectCount(Wrappers.<Placement>lambdaQuery()
			.eq(Placement::getMediaAccountId, mediaAccountId)
			.eq(Placement::getPlacementCode, code)) > 0)
			throw new IllegalArgumentException("广告位编码重复");
		Placement p = new Placement();
		p.setMediaAccountId(mediaAccountId);
		p.setPlacementCode(code);
		p.setPlacementName(name);
		p.setPlacementType(type);
		p.setSupportedCreatives(creatives);
		p.setStatus("ACTIVE");
		placementMapper.insert(p);
		MediaCapability cap = new MediaCapability();
		cap.setPlacementId(p.getId());
		cap.setBillingModes("[\"CPM\",\"CPC\",\"MEDIA_MANAGED_OCPC\"]");
		capabilityMapper.insert(cap);
		return p;
	}

	public Page<Placement> listPlacements(Page<Placement> page, String mediaAccountId, String type) {
		if (!StringUtils.hasText(mediaAccountId))
			throw new IllegalArgumentException("媒体投放账户不能为空");
		requireMediaAccount(mediaAccountId);
		var q = Wrappers.<Placement>lambdaQuery()
			.eq(Placement::getMediaAccountId, mediaAccountId)
			.eq(StringUtils.hasText(type), Placement::getPlacementType, type);
		return placementMapper.selectPage(page, q.orderByDesc(Placement::getCreateTime));
	}

	@Transactional
	public Placement updatePlacement(String id, String name, String type, String creatives) {
		Placement placement = requireWritablePlacement(id);
		if (StringUtils.hasText(name))
			placement.setPlacementName(name);
		if (StringUtils.hasText(type))
			placement.setPlacementType(type);
		if (creatives != null)
			placement.setSupportedCreatives(creatives);
		placementMapper.updateById(placement);
		return placement;
	}

	@Transactional
	public Placement updatePlacementStatus(String id, String status) {
		if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status))
			throw new IllegalArgumentException("广告位状态不合法");
		Placement placement = requireWritablePlacement(id);
		placement.setStatus(status);
		placementMapper.updateById(placement);
		return placement;
	}

	@Transactional
	public boolean deletePlacement(String id) {
		Placement placement = requireWritablePlacement(id);
		if (placementMapper.countReferences(id) > 0)
			throw new IllegalStateException("该广告位仍有质量快照或 Campaign 发布记录，请保留该广告位并改为停用");
		capabilityMapper
			.delete(Wrappers.<MediaCapability>lambdaQuery().eq(MediaCapability::getPlacementId, placement.getId()));
		return placementMapper.deleteById(placement.getId()) == 1;
	}

	public MediaCapability getCapability(String placementId) {
		requireReadablePlacement(placementId);
		MediaCapability c = capabilityMapper
			.selectOne(Wrappers.<MediaCapability>lambdaQuery().eq(MediaCapability::getPlacementId, placementId));
		if (c == null)
			throw new IllegalArgumentException("能力矩阵不存在");
		return c;
	}

	public MediaCapability updateCapability(String placementId, String billingModes, String targetEvents,
			String targetingFields, String creativeFormats, Boolean ocpc, Boolean sync, Boolean report) {
		Placement placement = placementMapper.selectById(placementId);
		if (placement == null)
			throw new IllegalArgumentException("资源不存在");
		requireWritableMediaAccount(placement.getMediaAccountId());
		MediaCapability c = getCapability(placementId);
		if (billingModes != null)
			c.setBillingModes(billingModes);
		if (targetEvents != null)
			c.setTargetEvents(targetEvents);
		if (targetingFields != null)
			c.setTargetingFields(targetingFields);
		if (creativeFormats != null)
			c.setCreativeFormats(creativeFormats);
		if (ocpc != null)
			c.setMediaManagedOcpc(ocpc ? 1 : 0);
		if (sync != null)
			c.setStatusSyncSupported(sync ? 1 : 0);
		if (report != null)
			c.setReportPullSupported(report ? 1 : 0);
		capabilityMapper.updateById(c);
		return c;
	}

	public PlacementQualitySnapshot getLatestQuality(String placementId) {
		requireReadablePlacement(placementId);
		return qualityMapper.selectOne(Wrappers.<PlacementQualitySnapshot>lambdaQuery()
			.eq(PlacementQualitySnapshot::getPlacementId, placementId)
			.orderByDesc(PlacementQualitySnapshot::getSnapshotDate)
			.last("LIMIT 1"));
	}

	public Page<PlacementQualitySnapshot> listQuality(Page<PlacementQualitySnapshot> page, String placementId) {
		requireReadablePlacement(placementId);
		return qualityMapper.selectPage(page,
				Wrappers.<PlacementQualitySnapshot>lambdaQuery()
					.eq(PlacementQualitySnapshot::getPlacementId, placementId)
					.orderByDesc(PlacementQualitySnapshot::getSnapshotDate));
	}

	private Placement requireReadablePlacement(String placementId) {
		Placement placement = placementMapper.selectById(placementId);
		if (placement == null)
			throw new IllegalArgumentException("资源不存在");
		requireMediaAccount(placement.getMediaAccountId());
		return placement;
	}

	private Placement requireWritablePlacement(String placementId) {
		Placement placement = placementMapper.selectById(placementId);
		if (placement == null)
			throw new IllegalArgumentException("资源不存在");
		requireWritableMediaAccount(placement.getMediaAccountId());
		return placement;
	}

	public Placement requirePlacementForMediaAccount(String placementId, String mediaAccountId) {
		MediaAccount account = requireMediaAccount(mediaAccountId);
		Placement placement = requireReadablePlacement(placementId);
		if (!mediaAccountId.equals(placement.getMediaAccountId())
				|| !account.getId().equals(placement.getMediaAccountId()))
			throw new IllegalArgumentException("资源不存在");
		return placement;
	}

	private MediaChannel requireMediaChannel(String mediaId, String channelId, boolean requireActive) {
		if (!StringUtils.hasText(channelId))
			throw new IllegalArgumentException("请选择投放渠道");
		MediaChannel channel = channelMapper.selectById(channelId);
		if (channel == null || !mediaId.equals(channel.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		if (requireActive && !"ACTIVE".equals(channel.getStatus()))
			throw new IllegalArgumentException("请选择已启用的投放渠道");
		return channel;
	}

}
