package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OcpxAdvertiserService {

	/** 广告主余额 Redis 单值 key（对齐 SGYZ RedisHelper 的 ADVERTISER_BALANCE 累加语义，无流水表）。 */
	private static final String BALANCE_PREFIX = "ocpx:advertiser:balance:";

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final StringRedisTemplate redis;

	public IPage<AdvertiserViews.Item> list(Page<OcpxAdvertiser> page, String keyword, String advertiserId) {
		return advertiserMapper
			.selectPage(page,
					Wrappers.<OcpxAdvertiser>lambdaQuery()
						.eq(StringUtils.hasText(advertiserId), OcpxAdvertiser::getId, advertiserId)
						.and(StringUtils.hasText(keyword) && !StringUtils.hasText(advertiserId),
								q -> q.like(OcpxAdvertiser::getId, keyword)
									.or()
									.like(OcpxAdvertiser::getAdvertiserName, keyword))
						.orderByDesc(OcpxAdvertiser::getUpdateTime))
			.convert(this::view);
	}

	/** 对齐 SGYZ getDspName：下拉只返回编号和名称。 */
	public List<AdvertiserViews.Option> options() {
		return advertiserMapper
			.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery().orderByDesc(OcpxAdvertiser::getId))
			.stream()
			.map(item -> new AdvertiserViews.Option(item.getId(), item.getAdvertiserName()))
			.toList();
	}

	public AdvertiserViews.Item detail(String id) {
		return view(require(id));
	}

	@Transactional
	public AdvertiserViews.Item create(AdvertiserRequests.Create request) {
		ensureUnique(null, request.name());
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		apply(advertiser, request.name(), request.credentialConfigKey(), request.bidUrl(), request.macroParams());
		advertiserMapper.insert(advertiser);
		configRefreshPublisher.mark();
		return detail(advertiser.getId());
	}

	@Transactional
	public AdvertiserViews.Item update(String id, AdvertiserRequests.Update request) {
		require(id);
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
		require(id);
		redis.opsForValue().increment(BALANCE_PREFIX + id, request.balance().doubleValue());
	}

	/** 从 Redis 读余额，无 key 时为 0（平移 SGYZ getDspInfoList 逐行回填）。 */
	private BigDecimal balance(String id) {
		String raw = redis.opsForValue().get(BALANCE_PREFIX + id);
		return StringUtils.hasText(raw) ? new BigDecimal(raw) : BigDecimal.ZERO;
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

	private AdvertiserViews.Item view(OcpxAdvertiser value) {
		return new AdvertiserViews.Item(value.getId(), value.getAdvertiserName(), value.getCredentialConfigKey(),
				value.getBidUrl(), value.getMacroParams(), balance(value.getId()), value.getCreateTime(),
				value.getUpdateTime());
	}

}
