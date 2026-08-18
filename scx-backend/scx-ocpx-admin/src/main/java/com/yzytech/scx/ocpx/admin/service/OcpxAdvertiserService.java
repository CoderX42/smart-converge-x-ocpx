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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxAdvertiserService {

	private static final String ROUTE_RATIO_PREFIX = "ocpx:route:ratio:";

	private static final String ROUTE_ORIGINAL_COUNT_PREFIX = "ocpx:route:original-count:";

	private static final String ROUTE_DELIVERED_COUNT_PREFIX = "ocpx:route:delivered-count:";

	private static final String ROUTE_DEDUCTION_PREFIX = "ocpx:route:deduction:";

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final StringRedisTemplate redis;

	private final AdvertiserAccountProvisioner accountProvisioner;

	private final AdvertiserScope advertiserScope;

	private final OcpxAdvertiserAccountService advertiserAccountService;

	public IPage<AdvertiserViews.Item> list(Page<OcpxAdvertiser> page, String keyword, String advertiserId) {
		String scopedAdvertiserId = advertiserScope.constrain(advertiserId);
		IPage<OcpxAdvertiser> result = advertiserMapper.selectPage(page, Wrappers.<OcpxAdvertiser>lambdaQuery()
			.eq(StringUtils.hasText(scopedAdvertiserId), OcpxAdvertiser::getId, scopedAdvertiserId)
			.and(StringUtils.hasText(keyword) && !StringUtils.hasText(scopedAdvertiserId),
					q -> q.like(OcpxAdvertiser::getId, keyword).or().like(OcpxAdvertiser::getAdvertiserName, keyword))
			.orderByDesc(OcpxAdvertiser::getUpdateTime));
		Map<String, String> usernames = usernames(result.getRecords());
		String accountOnlyId = advertiserScope.accountAdvertiserId(null);
		List<String> visibleAccountIds = accountOnlyId == null
				? result.getRecords().stream().map(OcpxAdvertiser::getId).toList()
				: result.getRecords().stream().map(OcpxAdvertiser::getId).filter(accountOnlyId::equals).toList();
		Map<String, BigDecimal> balances = advertiserAccountService.balancesByIds(visibleAccountIds);
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
		String visibleAccountId = advertiserScope.accountAdvertiserId(advertiser.getId());
		BigDecimal balance = advertiser.getId().equals(visibleAccountId)
				? advertiserAccountService.balance(advertiser.getId()) : null;
		return view(advertiser, username(advertiser.getId()), balance);
	}

	@Transactional
	public AdvertiserViews.Item create(AdvertiserRequests.Create request) {
		advertiserScope.requireAdministrator();
		ensureUnique(null, request.getName());
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		apply(advertiser, request.getName(), request.getCredentialConfigKey(), request.getBidUrl(),
				request.getMacroParams());
		if (advertiserMapper.insert(advertiser) != 1) {
			throw new IllegalStateException("广告主创建失败");
		}
		advertiserAccountService.create(advertiser.getId());
		AdvertiserAccountProvisioner.ProvisionedAccount account;
		try {
			account = accountProvisioner.provision(advertiser.getId(), advertiser.getAdvertiserName(),
					request.getUsername(), request.getPassword());
		}
		catch (IllegalArgumentException error) {
			throw new BootBusinessException(error.getMessage());
		}
		if (account == null || !StringUtils.hasText(account.getUserId())) {
			throw new IllegalStateException("广告主账号创建失败");
		}
		OcpxAdvertiser binding = new OcpxAdvertiser();
		binding.setId(advertiser.getId());
		binding.setAccountUserId(account.getUserId());
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
		ensureUnique(id, request.getName());
		OcpxAdvertiser update = new OcpxAdvertiser();
		update.setId(id);
		apply(update, request.getName(), request.getCredentialConfigKey(), request.getBidUrl(),
				request.getMacroParams());
		advertiserMapper.updateById(update);
		configRefreshPublisher.mark();
		return detail(id);
	}

	@Transactional
	public void delete(String id) {
		advertiserScope.requireAdministrator();
		OcpxAdvertiser current = requireForUpdate(id);
		advertiserAccountService.assertDeletable(id);
		List<ManagementRows.RouteKey> routes = advertiserMapper.selectCascadeRoutes(id);
		advertiserMapper.deleteReportMatTemp(id);
		advertiserMapper.deleteReportMat(id);
		advertiserMapper.deleteCallbackLogs(id);
		advertiserMapper.deleteFeedbackDayLogs(id);
		advertiserMapper.deleteFeedbackLogs(id);
		advertiserMapper.deleteFlowLogs(id);
		advertiserMapper.deleteCreativeBindings(id);
		advertiserMapper.deleteCreatives(id);
		advertiserMapper.deleteDeductionRules(id);
		advertiserMapper.deleteMonitorTasks(id);
		advertiserMapper.deletePromotionLinks(id);
		advertiserAccountService.deleteEmptyAccount(id);
		if (advertiserMapper.deleteById(id) != 1) {
			throw new IllegalStateException("广告主删除失败");
		}
		if (StringUtils.hasText(current.getAccountUserId())) {
			accountProvisioner.deprovision(id, current.getAccountUserId());
		}
		clearRuntimeState(id, routes);
		configRefreshPublisher.mark();
	}

	private void clearRuntimeState(String advertiserId, List<ManagementRows.RouteKey> routes) {
		Set<String> keys = new LinkedHashSet<>();
		for (ManagementRows.RouteKey route : routes) {
			if (!StringUtils.hasText(route.getPromotionLinkId()) || !StringUtils.hasText(route.getMediaId())) {
				continue;
			}
			String routeKey = route.getPromotionLinkId() + ":" + route.getMediaId();
			keys.add(ROUTE_RATIO_PREFIX + routeKey);
			keys.add(ROUTE_ORIGINAL_COUNT_PREFIX + routeKey);
			keys.add(ROUTE_DELIVERED_COUNT_PREFIX + routeKey);
			keys.add(ROUTE_DEDUCTION_PREFIX + "{" + routeKey + "}:state");
		}
		redis.delete(keys);
	}

	/** 幂等充值：账户锁、余额更新和不可变流水在同一 MySQL 事务内提交。 */
	public AdvertiserViews.RechargeResult recharge(String id, AdvertiserRequests.Recharge request) {
		advertiserScope.requireSystemAdministrator();
		OcpxAdvertiser current = require(id);
		return advertiserAccountService.recharge(current.getId(), request);
	}

	/** 幂等人工扣减：禁止透支，账户锁、余额更新和不可变流水在同一 MySQL 事务内提交。 */
	public AdvertiserViews.DeductionResult deduct(String id, AdvertiserRequests.Deduction request) {
		advertiserScope.requireSystemAdministrator();
		OcpxAdvertiser current = require(id);
		return advertiserAccountService.deduct(current.getId(), request);
	}

	public Map<String, BigDecimal> balancesByIds(List<String> advertiserIds) {
		return advertiserAccountService.balancesByIds(advertiserIds);
	}

	private Map<String, String> usernames(List<OcpxAdvertiser> advertisers) {
		if (advertisers.isEmpty()) {
			return Map.of();
		}
		return advertiserMapper.selectUsernamesByAdvertiserIds(advertisers.stream().map(OcpxAdvertiser::getId).toList())
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
			throw new BootBusinessException("广告主名称已存在");
		}
	}

	private OcpxAdvertiser require(String id) {
		OcpxAdvertiser value = advertiserMapper.selectById(id);
		if (value == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "广告主不存在");
		return value;
	}

	private OcpxAdvertiser requireForUpdate(String id) {
		OcpxAdvertiser value = advertiserMapper.selectByIdForUpdate(id);
		if (value == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "广告主不存在");
		return value;
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private AdvertiserViews.Item view(OcpxAdvertiser value, String username, BigDecimal balance) {
		return new AdvertiserViews.Item(value.getId(), value.getAdvertiserName(), username,
				value.getCredentialConfigKey(), value.getBidUrl(), value.getMacroParams(), balance,
				value.getCreateTime(), value.getUpdateTime());
	}

}
