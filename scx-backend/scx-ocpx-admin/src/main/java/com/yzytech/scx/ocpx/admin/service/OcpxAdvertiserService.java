package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.admin.dto.ManagementRows;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.system.api.service.AdvertiserAccountProvisioner;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OcpxAdvertiserService {

	/** 广告主余额 Redis 单值 key（对齐 SGYZ RedisHelper 的 ADVERTISER_BALANCE 累加语义，无流水表）。 */
	private static final String BALANCE_PREFIX = "ocpx:advertiser:balance:";

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final StringRedisTemplate redis;

	private final AdvertiserAccountProvisioner accountProvisioner;

	private final AdvertiserScope advertiserScope;

	public IPage<AdvertiserViews.Item> list(Page<OcpxAdvertiser> page, String keyword, String advertiserId) {
		String scopedAdvertiserId = advertiserScope.constrain(advertiserId);
		IPage<OcpxAdvertiser> result = advertiserMapper.selectPage(page,
				Wrappers.<OcpxAdvertiser>lambdaQuery()
					.eq(StringUtils.hasText(scopedAdvertiserId), OcpxAdvertiser::getId, scopedAdvertiserId)
					.and(StringUtils.hasText(keyword) && !StringUtils.hasText(scopedAdvertiserId),
								q -> q.like(OcpxAdvertiser::getId, keyword)
									.or()
									.like(OcpxAdvertiser::getAdvertiserName, keyword))
					.orderByDesc(OcpxAdvertiser::getUpdateTime));
		Map<String, String> usernames = usernames(result.getRecords());
		Map<String, BigDecimal> balances = balances(result.getRecords());
		return result.convert(value -> view(value, usernames.get(value.getId()), balances.get(value.getId())));
	}

	/** 对齐 SGYZ getDspName：下拉只返回编号和名称。 */
	public List<AdvertiserViews.Option> options() {
		String scopedAdvertiserId = advertiserScope.currentAdvertiserId();
		return advertiserMapper
			.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery()
				.eq(StringUtils.hasText(scopedAdvertiserId), OcpxAdvertiser::getId, scopedAdvertiserId)
				.orderByDesc(OcpxAdvertiser::getId))
			.stream()
			.map(item -> new AdvertiserViews.Option(item.getId(), item.getAdvertiserName()))
			.toList();
	}

	public AdvertiserViews.Item detail(String id) {
		OcpxAdvertiser advertiser = require(id);
		advertiserScope.requireOwner(advertiser.getId());
		return view(advertiser, username(advertiser.getId()), balance(advertiser.getId()));
	}

	@Transactional
	public AdvertiserViews.Item create(AdvertiserRequests.Create request) {
		advertiserScope.requireAdministrator();
		ensureUnique(null, request.name());
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		apply(advertiser, request.name(), request.credentialConfigKey(), request.bidUrl(), request.macroParams());
		if (advertiserMapper.insert(advertiser) != 1) {
			throw new IllegalStateException("广告主创建失败");
		}
		AdvertiserAccountProvisioner.ProvisionedAccount account = accountProvisioner.provision(advertiser.getId(),
				advertiser.getAdvertiserName(), request.username(), request.password());
		if (account == null || !StringUtils.hasText(account.userId())) {
			throw new IllegalStateException("广告主账号创建失败");
		}
		OcpxAdvertiser binding = new OcpxAdvertiser();
		binding.setId(advertiser.getId());
		binding.setAccountUserId(account.userId());
		if (advertiserMapper.updateById(binding) != 1) {
			throw new IllegalStateException("广告主账号绑定失败");
		}
		configRefreshPublisher.mark();
		return detail(advertiser.getId());
	}

	@Transactional
	public AdvertiserViews.Item update(String id, AdvertiserRequests.Update request) {
		OcpxAdvertiser current = require(id);
		advertiserScope.requireOwner(current.getId());
		ensureUnique(id, request.name());
		OcpxAdvertiser update = new OcpxAdvertiser();
		update.setId(id);
		apply(update, request.name(), request.credentialConfigKey(), request.bidUrl(), request.macroParams());
		advertiserMapper.updateById(update);
		configRefreshPublisher.mark();
		return detail(id);
	}

	/** 充值：对 Redis 余额单值做数值累加（平移 SGYZ rechargeBalance，无流水表、无幂等键）。 */
	public void recharge(String id, AdvertiserRequests.Recharge request) {
		OcpxAdvertiser current = require(id);
		advertiserScope.requireOwner(current.getId());
		redis.opsForValue().increment(BALANCE_PREFIX + id, request.balance().doubleValue());
	}

	/** 从 Redis 读余额，无 key 时为 0（平移 SGYZ getDspInfoList 逐行回填）。 */
	private BigDecimal balance(String id) {
		String raw = redis.opsForValue().get(BALANCE_PREFIX + id);
		return StringUtils.hasText(raw) ? new BigDecimal(raw) : BigDecimal.ZERO;
	}

	private Map<String, BigDecimal> balances(List<OcpxAdvertiser> advertisers) {
		if (advertisers.isEmpty()) {
			return Map.of();
		}
		List<String> keys = advertisers.stream().map(item -> BALANCE_PREFIX + item.getId()).toList();
		List<String> values = redis.opsForValue().multiGet(keys);
		Map<String, BigDecimal> result = new HashMap<>();
		for (int index = 0; index < advertisers.size(); index++) {
			String raw = values != null && index < values.size() ? values.get(index) : null;
			result.put(advertisers.get(index).getId(),
					StringUtils.hasText(raw) ? new BigDecimal(raw) : BigDecimal.ZERO);
		}
		return result;
	}

	private Map<String, String> usernames(List<OcpxAdvertiser> advertisers) {
		if (advertisers.isEmpty()) {
			return Map.of();
		}
		return advertiserMapper
			.selectUsernamesByAdvertiserIds(advertisers.stream().map(OcpxAdvertiser::getId).toList())
			.stream()
			.filter(row -> StringUtils.hasText(row.getAdvertiserId()) && StringUtils.hasText(row.getUsername()))
			.collect(java.util.stream.Collectors.toMap(ManagementRows.AdvertiserUsername::getAdvertiserId,
					ManagementRows.AdvertiserUsername::getUsername, (left, right) -> left));
	}

	private String username(String advertiserId) {
		return advertiserMapper.selectUsernamesByAdvertiserIds(List.of(advertiserId))
			.stream()
			.findFirst()
			.map(ManagementRows.AdvertiserUsername::getUsername)
			.orElse(null);
	}

	/** 对齐 SGYZ addDspInfo：请求地址按提交值落库，不做 URL 预检。 */
	private void apply(OcpxAdvertiser target, String name, String key, String bidUrl, String macros) {
		target.setAdvertiserName(name.trim());
		target.setCredentialConfigKey(trim(key));
		target.setBidUrl(bidUrl.trim());
		target.setMacroParams(trim(macros));
	}

	private void ensureUnique(String id, String name) {
		if (advertiserMapper.selectCount(Wrappers.<OcpxAdvertiser>lambdaQuery()
			.eq(OcpxAdvertiser::getAdvertiserName, name.trim())
			.ne(StringUtils.hasText(id), OcpxAdvertiser::getId, id)) > 0) {
			throw new IllegalArgumentException("广告主名称已存在");
		}
	}

	private OcpxAdvertiser require(String id) {
		OcpxAdvertiser value = advertiserMapper.selectById(id);
		if (value == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "广告主不存在");
		return value;
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private AdvertiserViews.Item view(OcpxAdvertiser value, String username, BigDecimal balance) {
		return new AdvertiserViews.Item(value.getId(), value.getAdvertiserName(), username,
				value.getCredentialConfigKey(), value.getBidUrl(), value.getMacroParams(), balance, value.getCreateTime(),
				value.getUpdateTime());
	}

}
