package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.CatalogRequests;
import com.yzytech.scx.adx.dto.CatalogViews;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxAppLink;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxDeductionRule;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxAppLinkMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxDeductionRuleMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import com.yzytech.scx.adx.mapper.AdxSystemUserMapper;
import com.yzytech.scx.adx.mapper.AdxSystemUserRoleMapper;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.entity.SysUserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdxCatalogService {

	private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

	private static final String ADVERTISER_ROLE_ID = "role_advertiser";

	private final AdxChannelMapper channelMapper;

	private final AdxAdvertiserMapper advertiserMapper;

	private final AdxAppLinkMapper appLinkMapper;

	private final AdxMonitorLinkMapper monitorMapper;

	private final AdxDeductionRuleMapper deductionMapper;

	private final AdxSystemUserMapper systemUserMapper;

	private final AdxSystemUserRoleMapper systemUserRoleMapper;

	private final AdxAccessScope accessScope;

	private final AdxConfigChangeService configChangeService;

	private final AdxEventService eventService;

	public IPage<CatalogViews.Channel> channels(Page<AdxChannel> page, String keyword) {
		return channelMapper
			.selectPage(page,
					Wrappers.<AdxChannel>lambdaQuery()
						.and(StringUtils.hasText(keyword),
								q -> q.like(AdxChannel::getChannelName, keyword).or().like(AdxChannel::getUri, keyword))
						.orderByDesc(AdxChannel::getCreateTime))
			.convert(this::channelView);
	}

	@Transactional
	public CatalogViews.Channel saveChannel(String id, CatalogRequests.SaveChannel request) {
		String publicBaseUri = validatePublicBaseUri(request.uri());
		ensureUniqueChannel(id, request.channelName(), request.channelCode());
		AdxChannel entity = StringUtils.hasText(id) ? requireChannel(id) : new AdxChannel();
		entity.setChannelCode(request.channelCode().trim());
		entity.setChannelName(request.channelName().trim());
		entity.setAdapterCode(request.adapterCode().trim().toUpperCase());
		entity.setAuthType(request.authType());
		entity.setKeyVersion(request.keyVersion());
		entity.setUri(publicBaseUri);
		entity.setMacroParams(request.macroParams().trim());
		entity.setCallbackHostAllowlist(request.callbackHostAllowlist().trim().toLowerCase());
		entity.setStatus(request.status());
		entity.setConfigVersion(entity.getConfigVersion() == null ? 1 : entity.getConfigVersion() + 1);
		if (StringUtils.hasText(request.secretKey())) {
			entity.setSecretKey(request.secretKey().trim());
		}
		if (StringUtils.hasText(id)) {
			channelMapper.updateById(entity);
		}
		else {
			channelMapper.insert(entity);
			eventService.initializeChannel(entity.getId());
		}
		if (StringUtils.hasText(id)) {
			configChangeService.channelChanged(id);
		}
		return channelView(entity);
	}

	@Transactional
	public void deleteChannel(String id) {
		requireChannel(id);
		if (monitorMapper.selectCount(Wrappers.<AdxMonitorLink>lambdaQuery().eq(AdxMonitorLink::getChannelId, id)) > 0
				|| deductionMapper
					.selectCount(Wrappers.<AdxDeductionRule>lambdaQuery().eq(AdxDeductionRule::getChannelId, id)) > 0) {
			throw new IllegalArgumentException("渠道仍被监测链接或扣量规则使用");
		}
		channelMapper.deleteById(id);
	}

	public IPage<CatalogViews.Advertiser> advertisers(Page<AdxAdvertiser> page, String keyword) {
		return advertiserMapper
			.selectPage(page,
					Wrappers.<AdxAdvertiser>lambdaQuery()
						.and(StringUtils.hasText(keyword),
								q -> q.like(AdxAdvertiser::getAdvertiserName, keyword)
									.or()
									.like(AdxAdvertiser::getAccount, keyword))
						.orderByDesc(AdxAdvertiser::getCreateTime))
			.convert(this::advertiserView);
	}

	@Transactional
	public CatalogViews.Advertiser saveAdvertiser(String id, CatalogRequests.SaveAdvertiser request) {
		ensureUniqueAdvertiser(id, request.advertiserName(), request.account());
		AdxAdvertiser entity = StringUtils.hasText(id) ? requireAdvertiser(id) : new AdxAdvertiser();
		if (!StringUtils.hasText(id) && !StringUtils.hasText(request.password())) {
			throw new IllegalArgumentException("新增广告主时密码必填");
		}
		entity.setAdvertiserName(request.advertiserName().trim());
		entity.setAdapterCode(request.adapterCode().trim().toUpperCase());
		entity.setBidUrl(request.bidUrl().trim());
		entity.setMacroParams(trimToNull(request.macroParams()));
		entity.setUpstreamHostAllowlist(request.upstreamHostAllowlist().trim().toLowerCase());
		entity.setAccount(request.account().trim());
		if (StringUtils.hasText(request.secretKey())) {
			entity.setSecretKey(request.secretKey().trim());
		}
		if (StringUtils.hasText(request.password())) {
			entity.setPasswordHash(PASSWORD_ENCODER.encode(request.password()));
		}
		if (entity.getBalance() == null) {
			entity.setBalance(BigDecimal.ZERO);
		}
		entity.setStatus(request.status());
		entity.setConfigVersion(entity.getConfigVersion() == null ? 1 : entity.getConfigVersion() + 1);
		if (StringUtils.hasText(id)) {
			advertiserMapper.updateById(entity);
		}
		else {
			advertiserMapper.insert(entity);
		}
		syncAdvertiserLogin(entity);
		if (StringUtils.hasText(id)) {
			configChangeService.advertiserChanged(id);
		}
		return advertiserView(entity);
	}

	@Transactional
	public void deleteAdvertiser(String id) {
		requireAdvertiser(id);
		if (appLinkMapper.selectCount(Wrappers.<AdxAppLink>lambdaQuery().eq(AdxAppLink::getAdvertiserId, id)) > 0
				|| monitorMapper
					.selectCount(Wrappers.<AdxMonitorLink>lambdaQuery().eq(AdxMonitorLink::getAdvertiserId, id)) > 0) {
			throw new IllegalArgumentException("广告主仍有关联的应用链接或监测链接");
		}
		advertiserMapper.deleteById(id);
		deleteAdvertiserLogin(id);
	}

	public IPage<CatalogViews.AppLink> appLinks(Page<AdxAppLink> page, String advertiserId, String keyword) {
		Page<AdxAppLink> result = appLinkMapper.selectPage(page,
				Wrappers.<AdxAppLink>lambdaQuery()
					.eq(StringUtils.hasText(advertiserId), AdxAppLink::getAdvertiserId, advertiserId)
					.and(StringUtils.hasText(keyword),
							q -> q.like(AdxAppLink::getLinkName, keyword).or().like(AdxAppLink::getLinkUrl, keyword))
					.orderByDesc(AdxAppLink::getCreateTime));
		Map<String, String> advertiserNames = advertiserNames(
				result.getRecords().stream().map(AdxAppLink::getAdvertiserId).toList());
		return result.convert(entity -> appLinkView(entity, advertiserNames.get(entity.getAdvertiserId())));
	}

	@Transactional
	public CatalogViews.AppLink saveAppLink(String id, CatalogRequests.SaveAppLink request) {
		AdxAdvertiser advertiser = requireAdvertiser(request.advertiserId());
		if (!List.of(1, 2, 3, 4).contains(request.linkPlatform())) {
			throw new IllegalArgumentException("应用平台无效");
		}
		AdxAppLink entity = StringUtils.hasText(id) ? requireAppLink(id) : new AdxAppLink();
		entity.setAdvertiserId(request.advertiserId());
		entity.setLinkPlatform(request.linkPlatform());
		entity.setLinkName(request.linkName().trim());
		entity.setLinkChannel(trimToNull(request.linkChannel()));
		entity.setLinkUrl(request.linkUrl().trim());
		entity.setDeeplink(trimToNull(request.deeplink()));
		entity.setLinkExt(trimToNull(request.linkExt()));
		entity.setLinkRemark(trimToNull(request.linkRemark()));
		if (StringUtils.hasText(id)) {
			appLinkMapper.updateById(entity);
			configChangeService.appLinkChanged(id);
		}
		else {
			appLinkMapper.insert(entity);
		}
		return appLinkView(entity, advertiser.getAdvertiserName());
	}

	@Transactional
	public void deleteAppLink(String id) {
		requireAppLink(id);
		if (monitorMapper.selectCount(Wrappers.<AdxMonitorLink>lambdaQuery().eq(AdxMonitorLink::getAppLinkId, id)) > 0
				|| deductionMapper
					.selectCount(Wrappers.<AdxDeductionRule>lambdaQuery().eq(AdxDeductionRule::getAppLinkId, id)) > 0) {
			throw new IllegalArgumentException("应用链接仍被监测链接或扣量规则使用");
		}
		appLinkMapper.deleteById(id);
	}

	public IPage<CatalogViews.Monitor> monitors(Page<AdxMonitorLink> page, String channelId, String advertiserId,
			String keyword) {
		Page<AdxMonitorLink> result = monitorMapper.selectPage(page, Wrappers.<AdxMonitorLink>lambdaQuery()
			.eq(StringUtils.hasText(channelId), AdxMonitorLink::getChannelId, channelId)
			.eq(StringUtils.hasText(advertiserId), AdxMonitorLink::getAdvertiserId, advertiserId)
			.and(StringUtils.hasText(keyword),
					q -> q.like(AdxMonitorLink::getMonitorName, keyword).or().like(AdxMonitorLink::getBizId, keyword))
			.orderByDesc(AdxMonitorLink::getCreateTime));
		return decorateMonitors(result);
	}

	@Transactional
	public CatalogViews.Monitor saveMonitor(String id, CatalogRequests.SaveMonitor request) {
		AdxChannel channel = requireChannel(request.channelId());
		AdxAdvertiser advertiser = requireAdvertiser(request.advertiserId());
		AdxAppLink appLink = requireAppLink(request.appLinkId());
		if (!request.advertiserId().equals(appLink.getAdvertiserId())) {
			throw new IllegalArgumentException("应用链接不属于所选广告主");
		}
		AdxMonitorLink entity = StringUtils.hasText(id) ? requireMonitor(id) : new AdxMonitorLink();
		entity.setChannelId(request.channelId());
		entity.setAdvertiserId(request.advertiserId());
		entity.setAppLinkId(request.appLinkId());
		entity.setMonitorName(request.monitorName().trim());
		entity.setBizId(trimToNull(request.bizId()));
		entity.setUnitPrice(request.unitPrice());
		entity.setRemark(trimToNull(request.remark()));
		entity.setUpstreamFeedbackUrl(trimToNull(request.upstreamFeedbackUrl()));
		entity.setDownstreamCallbackUrl(trimToNull(request.downstreamCallbackUrl()));
		entity.setClickDedupWindowSeconds(
				request.clickDedupWindowSeconds() == null ? 3600 : request.clickDedupWindowSeconds());
		entity.setRtaMode(request.rtaMode());
		entity.setBillingMode(request.billingMode());
		entity.setRouteStatus("DRAFT");
		entity.setPublishedAt(null);
		entity.setPublishedUpstreamFeedbackUrl(null);
		entity.setConfigVersion(entity.getConfigVersion() == null ? 1 : entity.getConfigVersion() + 1);
		if (!StringUtils.hasText(id)) {
			monitorMapper.insert(entity);
		}
		String feedbackUrl = feedbackUrl(channel.getUri(), channel.getChannelCode());
		entity.setShowUrl(feedbackUrl);
		entity.setClickUrl(feedbackUrl);
		monitorMapper.updateById(entity);
		if (request.eventBindings() != null) {
			eventService.replaceBindings(entity.getId(), entity.getChannelId(), request.eventBindings());
		}
		configChangeService.enqueueMonitor(entity.getId(), entity.getConfigVersion(), "ROUTE_DRAFTED");
		return monitorView(entity, channel.getChannelName(), advertiser.getAdvertiserName(), appLink.getLinkName(),
				advertiser.getBidUrl(), eventService.bindings(entity.getId()), null);
	}

	public CatalogViews.Monitor monitor(String id) {
		AdxMonitorLink entity = requireMonitor(id);
		AdxChannel channel = requireChannel(entity.getChannelId());
		AdxAdvertiser advertiser = requireAdvertiser(entity.getAdvertiserId());
		AdxAppLink appLink = requireAppLink(entity.getAppLinkId());
		return monitorView(entity, channel.getChannelName(), advertiser.getAdvertiserName(), appLink.getLinkName(),
				advertiser.getBidUrl(), eventService.bindings(id), null);
	}

	@Transactional
	public void deleteMonitor(String id) {
		AdxMonitorLink monitor = requireMonitor(id);
		eventService.replaceBindings(id, monitor.getChannelId(), List.of());
		monitorMapper.deleteById(monitor);
		configChangeService.enqueueMonitor(id, monitor.getConfigVersion() == null ? 1 : monitor.getConfigVersion() + 1,
				"ROUTE_DELETED");
	}

	public IPage<CatalogViews.Deduction> deductions(Page<AdxDeductionRule> page, String channelId, String appLinkId) {
		Page<AdxDeductionRule> result = deductionMapper.selectPage(page,
				Wrappers.<AdxDeductionRule>lambdaQuery()
					.eq(StringUtils.hasText(channelId), AdxDeductionRule::getChannelId, channelId)
					.eq(StringUtils.hasText(appLinkId), AdxDeductionRule::getAppLinkId, appLinkId)
					.orderByDesc(AdxDeductionRule::getCreateTime));
		return decorateDeductions(result);
	}

	public Map<String, Object> reportOptions() {
		String advertiserScope = accessScope.currentAdvertiserId();
		List<AdxAdvertiser> advertiserRows = advertiserMapper.selectList(Wrappers.<AdxAdvertiser>lambdaQuery()
			.eq(StringUtils.hasText(advertiserScope), AdxAdvertiser::getId, advertiserScope)
			.orderByAsc(AdxAdvertiser::getAdvertiserName));
		Map<String, String> advertiserNames = advertiserRows.stream()
			.collect(Collectors.toMap(AdxAdvertiser::getId, AdxAdvertiser::getAdvertiserName));
		Map<String, String> advertiserBidUrls = advertiserRows.stream()
			.collect(Collectors.toMap(AdxAdvertiser::getId, AdxAdvertiser::getBidUrl));
		List<AdxAppLink> linkRows = appLinkMapper.selectList(Wrappers.<AdxAppLink>lambdaQuery()
			.eq(StringUtils.hasText(advertiserScope), AdxAppLink::getAdvertiserId, advertiserScope)
			.orderByAsc(AdxAppLink::getLinkName));
		Map<String, String> linkNames = linkRows.stream()
			.collect(Collectors.toMap(AdxAppLink::getId, AdxAppLink::getLinkName));
		List<AdxMonitorLink> monitorRows = monitorMapper.selectList(Wrappers.<AdxMonitorLink>lambdaQuery()
			.eq(StringUtils.hasText(advertiserScope), AdxMonitorLink::getAdvertiserId, advertiserScope)
			.orderByAsc(AdxMonitorLink::getMonitorName));
		List<String> channelIds = monitorRows.stream().map(AdxMonitorLink::getChannelId).distinct().toList();
		List<AdxChannel> channelRows = StringUtils.hasText(advertiserScope)
				? (channelIds.isEmpty() ? List.of() : channelMapper.selectBatchIds(channelIds))
				: channelMapper.selectList(Wrappers.<AdxChannel>lambdaQuery().orderByAsc(AdxChannel::getChannelName));
		Map<String, String> channelNames = channelRows.stream()
			.collect(Collectors.toMap(AdxChannel::getId, AdxChannel::getChannelName));
		Map<String, com.yzytech.scx.adx.dto.EventViews.BindingSummary> monitorSummaries = eventService
			.bindingSummaries(monitorRows.stream().map(AdxMonitorLink::getId).toList());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("channels", channelRows.stream().map(this::channelView).toList());
		result.put("advertisers", advertiserRows.stream().map(this::advertiserView).toList());
		result.put("appLinks",
				linkRows.stream().map(row -> appLinkView(row, advertiserNames.get(row.getAdvertiserId()))).toList());
		result.put("monitors",
				monitorRows.stream()
					.map(row -> monitorView(row, channelNames.get(row.getChannelId()),
							advertiserNames.get(row.getAdvertiserId()), linkNames.get(row.getAppLinkId()),
							advertiserBidUrls.get(row.getAdvertiserId()), List.of(), monitorSummaries.get(row.getId())))
					.toList());
		return result;
	}

	@Transactional
	public CatalogViews.Deduction saveDeduction(String id, CatalogRequests.SaveDeduction request) {
		AdxChannel channel = requireChannel(request.channelId());
		AdxAppLink appLink = requireAppLink(request.appLinkId());
		long duplicate = deductionMapper.selectCount(Wrappers.<AdxDeductionRule>lambdaQuery()
			.eq(AdxDeductionRule::getChannelId, request.channelId())
			.eq(AdxDeductionRule::getAppLinkId, request.appLinkId())
			.ne(StringUtils.hasText(id), AdxDeductionRule::getId, id));
		if (duplicate > 0) {
			throw new IllegalArgumentException("该渠道和应用链接已配置扣量规则");
		}
		AdxDeductionRule entity = StringUtils.hasText(id) ? requireDeduction(id) : new AdxDeductionRule();
		entity.setChannelId(request.channelId());
		entity.setAppLinkId(request.appLinkId());
		entity.setRatio(request.ratio());
		entity.setConfigVersion(entity.getConfigVersion() == null ? 1 : entity.getConfigVersion() + 1);
		if (StringUtils.hasText(id)) {
			deductionMapper.updateById(entity);
		}
		else {
			deductionMapper.insert(entity);
		}
		configChangeService.deductionChanged(request.channelId(), request.appLinkId());
		return deductionView(entity, channel.getChannelName(), appLink.getLinkName());
	}

	@Transactional
	public void deleteDeduction(String id) {
		AdxDeductionRule rule = requireDeduction(id);
		deductionMapper.deleteById(rule);
		configChangeService.deductionChanged(rule.getChannelId(), rule.getAppLinkId());
	}

	private IPage<CatalogViews.Monitor> decorateMonitors(Page<AdxMonitorLink> source) {
		Map<String, String> channels = channelNames(
				source.getRecords().stream().map(AdxMonitorLink::getChannelId).toList());
		Map<String, String> advertisers = advertiserNames(
				source.getRecords().stream().map(AdxMonitorLink::getAdvertiserId).toList());
		Map<String, String> advertiserBidUrls = advertiserBidUrls(
				source.getRecords().stream().map(AdxMonitorLink::getAdvertiserId).toList());
		Map<String, String> links = appLinkNames(
				source.getRecords().stream().map(AdxMonitorLink::getAppLinkId).toList());
		Map<String, com.yzytech.scx.adx.dto.EventViews.BindingSummary> summaries = eventService
			.bindingSummaries(source.getRecords().stream().map(AdxMonitorLink::getId).toList());
		return source.convert(entity -> monitorView(entity, channels.get(entity.getChannelId()),
				advertisers.get(entity.getAdvertiserId()), links.get(entity.getAppLinkId()),
				advertiserBidUrls.get(entity.getAdvertiserId()), List.of(), summaries.get(entity.getId())));
	}

	private IPage<CatalogViews.Deduction> decorateDeductions(Page<AdxDeductionRule> source) {
		Map<String, String> channels = channelNames(
				source.getRecords().stream().map(AdxDeductionRule::getChannelId).toList());
		Map<String, String> links = appLinkNames(
				source.getRecords().stream().map(AdxDeductionRule::getAppLinkId).toList());
		return source.convert(
				entity -> deductionView(entity, channels.get(entity.getChannelId()), links.get(entity.getAppLinkId())));
	}

	private Map<String, String> channelNames(List<String> ids) {
		return names(ids, channelMapper::selectBatchIds, AdxChannel::getId, AdxChannel::getChannelName);
	}

	private Map<String, String> advertiserNames(List<String> ids) {
		return names(ids, advertiserMapper::selectBatchIds, AdxAdvertiser::getId, AdxAdvertiser::getAdvertiserName);
	}

	private Map<String, String> advertiserBidUrls(List<String> ids) {
		return names(ids, advertiserMapper::selectBatchIds, AdxAdvertiser::getId, AdxAdvertiser::getBidUrl);
	}

	private Map<String, String> appLinkNames(List<String> ids) {
		return names(ids, appLinkMapper::selectBatchIds, AdxAppLink::getId, AdxAppLink::getLinkName);
	}

	private <T> Map<String, String> names(List<String> ids, Function<List<String>, List<T>> loader,
			Function<T, String> id, Function<T, String> name) {
		List<String> distinct = ids.stream().filter(StringUtils::hasText).distinct().toList();
		return distinct.isEmpty() ? Map.of()
				: loader.apply(distinct).stream().collect(Collectors.toMap(id, name, (left, right) -> left));
	}

	private AdxChannel requireChannel(String id) {
		return require(channelMapper.selectById(id), "渠道不存在");
	}

	private AdxAdvertiser requireAdvertiser(String id) {
		return require(advertiserMapper.selectById(id), "广告主不存在");
	}

	private AdxAppLink requireAppLink(String id) {
		return require(appLinkMapper.selectById(id), "应用链接不存在");
	}

	private AdxMonitorLink requireMonitor(String id) {
		return require(monitorMapper.selectById(id), "监测链接不存在");
	}

	private AdxDeductionRule requireDeduction(String id) {
		return require(deductionMapper.selectById(id), "扣量规则不存在");
	}

	private <T> T require(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private void ensureUniqueChannel(String id, String name, String code) {
		if (channelMapper.selectCount(Wrappers.<AdxChannel>lambdaQuery()
			.and(q -> q.eq(AdxChannel::getChannelName, name.trim()).or().eq(AdxChannel::getChannelCode, code.trim()))
			.ne(StringUtils.hasText(id), AdxChannel::getId, id)) > 0) {
			throw new IllegalArgumentException("渠道名称或渠道编码已存在");
		}
	}

	private void ensureUniqueAdvertiser(String id, String name, String account) {
		if (advertiserMapper.selectCount(Wrappers.<AdxAdvertiser>lambdaQuery()
			.and(q -> q.eq(AdxAdvertiser::getAdvertiserName, name.trim())
				.or()
				.eq(AdxAdvertiser::getAccount, account.trim()))
			.ne(StringUtils.hasText(id), AdxAdvertiser::getId, id)) > 0) {
			throw new IllegalArgumentException("广告主名称或登录账号已存在");
		}
		SysUser linked = systemUserMapper
			.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getShopId, id).eq(SysUser::getType, "2"));
		if (systemUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
			.eq(SysUser::getUsername, account.trim())
			.ne(linked != null, SysUser::getId, linked == null ? null : linked.getId())) > 0) {
			throw new IllegalArgumentException("登录账号已被系统用户占用");
		}
	}

	private void syncAdvertiserLogin(AdxAdvertiser advertiser) {
		SysUser user = systemUserMapper.selectOne(
				Wrappers.<SysUser>lambdaQuery().eq(SysUser::getShopId, advertiser.getId()).eq(SysUser::getType, "2"));
		boolean create = user == null;
		if (create) {
			user = new SysUser();
			user.setType("2");
			user.setShopId(advertiser.getId());
			user.setStatus("0");
			user.setDelFlag("0");
		}
		user.setUsername(advertiser.getAccount());
		user.setNickname(advertiser.getAdvertiserName());
		user.setPassword(advertiser.getPasswordHash());
		if (create) {
			systemUserMapper.insert(user);
		}
		else {
			systemUserMapper.updateById(user);
		}
		if (systemUserRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
			.eq(SysUserRole::getUserId, user.getId())
			.eq(SysUserRole::getRoleId, ADVERTISER_ROLE_ID)) == 0) {
			SysUserRole role = new SysUserRole();
			role.setUserId(user.getId());
			role.setRoleId(ADVERTISER_ROLE_ID);
			systemUserRoleMapper.insert(role);
		}
	}

	private void deleteAdvertiserLogin(String advertiserId) {
		SysUser user = systemUserMapper
			.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getShopId, advertiserId).eq(SysUser::getType, "2"));
		if (user == null) {
			return;
		}
		systemUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, user.getId()));
		systemUserMapper.deleteById(user.getId());
	}

	private String feedbackUrl(String publicBaseUri, String channelCode) {
		String normalized = validatePublicBaseUri(publicBaseUri);
		if (normalized.endsWith("/feedback")) {
			return normalized;
		}
		return normalized + "/open-api/ocpx/v1/channels/"
				+ URLEncoder.encode(channelCode, StandardCharsets.UTF_8).replace("+", "%20") + "/feedback";
	}

	private String validatePublicBaseUri(String value) {
		String normalized = value.trim().replaceAll("/+$", "");
		URI uri;
		try {
			uri = URI.create(normalized);
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("SCX OCPX引擎地址无效", e);
		}
		if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
				|| !StringUtils.hasText(uri.getHost()) || uri.getRawUserInfo() != null || uri.getRawQuery() != null
				|| uri.getRawFragment() != null) {
			throw new IllegalArgumentException("SCX OCPX引擎地址必须是无账号、query和fragment的HTTP(S)地址");
		}
		return normalized;
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private CatalogViews.Channel channelView(AdxChannel entity) {
		return new CatalogViews.Channel(entity.getId(), entity.getChannelCode(), entity.getChannelName(),
				entity.getAdapterCode(), entity.getAuthType(), entity.getKeyVersion(), entity.getUri(),
				StringUtils.hasText(entity.getSecretKey()), entity.getMacroParams(), entity.getCallbackHostAllowlist(),
				entity.getStatus(), entity.getConfigVersion(), entity.getCreateTime(), entity.getUpdateTime());
	}

	private CatalogViews.Advertiser advertiserView(AdxAdvertiser entity) {
		return new CatalogViews.Advertiser(entity.getId(), entity.getAdvertiserName(), entity.getAdapterCode(),
				entity.getBidUrl(), StringUtils.hasText(entity.getSecretKey()), entity.getMacroParams(),
				entity.getUpstreamHostAllowlist(), entity.getAccount(), entity.getBalance(), entity.getStatus(),
				entity.getConfigVersion(), entity.getCreateTime(), entity.getUpdateTime());
	}

	private CatalogViews.AppLink appLinkView(AdxAppLink entity, String advertiserName) {
		return new CatalogViews.AppLink(entity.getId(), entity.getAdvertiserId(), advertiserName,
				entity.getLinkPlatform(), entity.getLinkName(), entity.getLinkChannel(), entity.getLinkUrl(),
				entity.getDeeplink(), entity.getLinkExt(), entity.getLinkRemark(), entity.getCreateTime(),
				entity.getUpdateTime());
	}

	private CatalogViews.Monitor monitorView(AdxMonitorLink entity, String channelName, String advertiserName,
			String appLinkName, String advertiserBidUrl, List<com.yzytech.scx.adx.dto.EventViews.Binding> bindings,
			com.yzytech.scx.adx.dto.EventViews.BindingSummary summary) {
		String overrideUrl = entity.getUpstreamFeedbackUrl();
		boolean published = "PUBLISHED".equals(entity.getRouteStatus())
				&& StringUtils.hasText(entity.getPublishedUpstreamFeedbackUrl());
		String effectiveUrl = published ? entity.getPublishedUpstreamFeedbackUrl()
				: StringUtils.hasText(overrideUrl) ? overrideUrl : advertiserBidUrl;
		String source = (published ? "" : "DRAFT_")
				+ (StringUtils.hasText(overrideUrl) ? "MONITOR_OVERRIDE" : "ADVERTISER_DEFAULT");
		return new CatalogViews.Monitor(entity.getId(), entity.getChannelId(), channelName, entity.getAdvertiserId(),
				advertiserName, entity.getAppLinkId(), appLinkName, entity.getMonitorName(), entity.getBizId(),
				entity.getUnitPrice(), entity.getRemark(), entity.getShowUrl(), entity.getClickUrl(),
				entity.getUpstreamFeedbackUrl(), effectiveUrl, source, entity.getDownstreamCallbackUrl(),
				entity.getClickDedupWindowSeconds(), entity.getRouteStatus(), entity.getConfigVersion(),
				entity.getRtaMode(), entity.getBillingMode(), entity.getPublishedAt(), entity.getCreateTime(),
				entity.getUpdateTime(), bindings == null ? List.of() : bindings,
				summary == null ? (bindings == null ? 0 : bindings.size()) : summary.bindingCount(),
				summary == null ? null : summary.summary());
	}

	private CatalogViews.Deduction deductionView(AdxDeductionRule entity, String channelName, String appLinkName) {
		return new CatalogViews.Deduction(entity.getId(), entity.getChannelId(), channelName, entity.getAppLinkId(),
				appLinkName, entity.getRatio(), entity.getConfigVersion(), entity.getCreateTime(),
				entity.getUpdateTime());
	}

}
