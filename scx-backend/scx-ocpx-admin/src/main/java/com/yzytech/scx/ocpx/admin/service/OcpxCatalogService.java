package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.dto.CatalogRequests;
import com.yzytech.scx.ocpx.admin.dto.CatalogViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxDeductionRule;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.PermissionLevel;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.Restriction;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.ScopeType;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.Snapshot;
import lombok.RequiredArgsConstructor;
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
public class OcpxCatalogService {

	private final OcpxChannelMapper channelMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxAppLinkMapper appLinkMapper;

	private final OcpxMonitorLinkMapper monitorMapper;

	private final OcpxDeductionRuleMapper deductionMapper;

	private final OcpxAccessScope accessScope;

	private final OcpxConfigChangeService configChangeService;

	private final OcpxEventService eventService;

	private final OcpxUrlPolicy urlPolicy;

	private final OcpxTrackingProperties trackingProperties;

	public IPage<CatalogViews.Channel> channels(Page<OcpxChannel> page, String keyword) {
		Restriction restriction = accessScope.current().restriction(ScopeType.CHANNEL, PermissionLevel.VIEW);
		if (isEmpty(restriction)) {
			return emptyPage(page);
		}
		return channelMapper
			.selectPage(page,
					Wrappers.<OcpxChannel>lambdaQuery()
						.in(!restriction.unrestricted(), OcpxChannel::getId, restriction.ids())
						.and(StringUtils.hasText(keyword),
								q -> q.like(OcpxChannel::getChannelName, keyword).or().like(OcpxChannel::getUri, keyword))
						.orderByDesc(OcpxChannel::getCreateTime))
			.convert(this::channelView);
	}

	@Transactional
	public CatalogViews.Channel saveChannel(String id, CatalogRequests.SaveChannel request) {
		OcpxChannel entity;
		if (StringUtils.hasText(id)) {
			entity = requireChannel(id, PermissionLevel.OPERATE);
		}
		else {
			accessScope.requireAll(PermissionLevel.MANAGE);
			entity = new OcpxChannel();
		}
		String publicBaseUri = validatePublicBaseUri(
				firstNonBlank(request.uri(), entity.getUri(), trackingProperties.getPublicBaseUrl()));
		ensureUniqueChannel(id, request.channelName(), request.channelCode());
		entity.setChannelCode(request.channelCode().trim());
		entity.setChannelName(request.channelName().trim());
		entity.setAdapterCode(request.adapterCode().trim().toUpperCase());
		entity.setAuthType(firstNonBlank(request.authType(), entity.getAuthType(), "HMAC"));
		entity.setKeyVersion(request.keyVersion() == null
				? (entity.getKeyVersion() == null ? 1 : entity.getKeyVersion()) : request.keyVersion());
		entity.setUri(publicBaseUri);
		entity.setMacroParams(request.macroParams().trim());
		if (request.callbackHostAllowlist() != null) {
			entity.setCallbackHostAllowlist(trimToNull(request.callbackHostAllowlist()) == null ? null
					: request.callbackHostAllowlist().trim().toLowerCase());
		}
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
		requireChannel(id, PermissionLevel.MANAGE);
		if (monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery().eq(OcpxMonitorLink::getChannelId, id)) > 0
				|| deductionMapper
					.selectCount(Wrappers.<OcpxDeductionRule>lambdaQuery().eq(OcpxDeductionRule::getChannelId, id)) > 0) {
			throw new IllegalArgumentException("渠道仍被监测链接或扣量规则使用");
		}
		channelMapper.deleteById(id);
	}

	public IPage<CatalogViews.AppLink> appLinks(Page<OcpxAppLink> page, String advertiserId, String keyword) {
		Restriction restriction = accessScope.current().restriction(ScopeType.ADVERTISER, PermissionLevel.VIEW);
		if (StringUtils.hasText(advertiserId)) {
			accessScope.require(ScopeType.ADVERTISER, advertiserId, PermissionLevel.VIEW);
		}
		if (isEmpty(restriction)) {
			return emptyPage(page);
		}
		Page<OcpxAppLink> result = appLinkMapper.selectPage(page,
				Wrappers.<OcpxAppLink>lambdaQuery()
					.in(!restriction.unrestricted(), OcpxAppLink::getAdvertiserId, restriction.ids())
					.eq(StringUtils.hasText(advertiserId), OcpxAppLink::getAdvertiserId, advertiserId)
					.and(StringUtils.hasText(keyword),
							q -> q.like(OcpxAppLink::getLinkName, keyword).or().like(OcpxAppLink::getLinkUrl, keyword))
					.orderByDesc(OcpxAppLink::getCreateTime));
		Map<String, String> advertiserNames = advertiserNames(
				result.getRecords().stream().map(OcpxAppLink::getAdvertiserId).toList());
		return result.convert(entity -> appLinkView(entity, advertiserNames.get(entity.getAdvertiserId())));
	}

	@Transactional
	public CatalogViews.AppLink saveAppLink(String id, CatalogRequests.SaveAppLink request) {
		OcpxAdvertiser advertiser = requireAdvertiser(request.advertiserId(), PermissionLevel.OPERATE);
		if (!List.of(1, 2, 3, 4).contains(request.linkPlatform())) {
			throw new IllegalArgumentException("应用平台无效");
		}
		OcpxAppLink entity = StringUtils.hasText(id) ? requireAppLink(id, PermissionLevel.OPERATE) : new OcpxAppLink();
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
		requireAppLink(id, PermissionLevel.MANAGE);
		if (monitorMapper.selectCount(Wrappers.<OcpxMonitorLink>lambdaQuery().eq(OcpxMonitorLink::getAppLinkId, id)) > 0
				|| deductionMapper
					.selectCount(Wrappers.<OcpxDeductionRule>lambdaQuery().eq(OcpxDeductionRule::getAppLinkId, id)) > 0) {
			throw new IllegalArgumentException("应用链接仍被监测链接或扣量规则使用");
		}
		appLinkMapper.deleteById(id);
	}

	public IPage<CatalogViews.Monitor> monitors(Page<OcpxMonitorLink> page, String channelId, String advertiserId,
			String keyword) {
		Snapshot snapshot = accessScope.current();
		Restriction channels = snapshot.restriction(ScopeType.CHANNEL, PermissionLevel.VIEW);
		Restriction advertisers = snapshot.restriction(ScopeType.ADVERTISER, PermissionLevel.VIEW);
		if (StringUtils.hasText(channelId)) {
			accessScope.require(ScopeType.CHANNEL, channelId, PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(advertiserId)) {
			accessScope.require(ScopeType.ADVERTISER, advertiserId, PermissionLevel.VIEW);
		}
		if (isEmpty(channels) || isEmpty(advertisers)) {
			return emptyPage(page);
		}
		Page<OcpxMonitorLink> result = monitorMapper.selectPage(page, Wrappers.<OcpxMonitorLink>lambdaQuery()
			.in(!channels.unrestricted(), OcpxMonitorLink::getChannelId, channels.ids())
			.in(!advertisers.unrestricted(), OcpxMonitorLink::getAdvertiserId, advertisers.ids())
			.eq(StringUtils.hasText(channelId), OcpxMonitorLink::getChannelId, channelId)
			.eq(StringUtils.hasText(advertiserId), OcpxMonitorLink::getAdvertiserId, advertiserId)
			.and(StringUtils.hasText(keyword),
					q -> q.like(OcpxMonitorLink::getMonitorName, keyword).or().like(OcpxMonitorLink::getBizId, keyword))
			.orderByDesc(OcpxMonitorLink::getCreateTime));
		return decorateMonitors(result);
	}

	@Transactional
	public CatalogViews.Monitor saveMonitor(String id, CatalogRequests.SaveMonitor request) {
		OcpxChannel channel = requireChannel(request.channelId(), PermissionLevel.OPERATE);
		OcpxAdvertiser advertiser = requireAdvertiser(request.advertiserId(), PermissionLevel.OPERATE);
		OcpxAppLink appLink = requireAppLink(request.appLinkId(), PermissionLevel.OPERATE);
		if (!request.advertiserId().equals(appLink.getAdvertiserId())) {
			throw new IllegalArgumentException("应用链接不属于所选广告主");
		}
		OcpxMonitorLink entity = StringUtils.hasText(id) ? requireMonitor(id, PermissionLevel.OPERATE)
				: new OcpxMonitorLink();
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
		OcpxMonitorLink entity = requireMonitor(id, PermissionLevel.VIEW);
		OcpxChannel channel = requireChannel(entity.getChannelId());
		OcpxAdvertiser advertiser = requireAdvertiser(entity.getAdvertiserId());
		OcpxAppLink appLink = requireAppLink(entity.getAppLinkId());
		return monitorView(entity, channel.getChannelName(), advertiser.getAdvertiserName(), appLink.getLinkName(),
				advertiser.getBidUrl(), eventService.bindings(id), null);
	}

	@Transactional
	public void deleteMonitor(String id) {
		OcpxMonitorLink monitor = requireMonitor(id, PermissionLevel.MANAGE);
		eventService.replaceBindings(id, monitor.getChannelId(), List.of());
		monitorMapper.deleteById(monitor);
		configChangeService.enqueueMonitor(id, monitor.getConfigVersion() == null ? 1 : monitor.getConfigVersion() + 1,
				"ROUTE_DELETED");
	}

	public IPage<CatalogViews.Deduction> deductions(Page<OcpxDeductionRule> page, String channelId, String appLinkId) {
		Snapshot snapshot = accessScope.current();
		Restriction channels = snapshot.restriction(ScopeType.CHANNEL, PermissionLevel.VIEW);
		Restriction advertisers = snapshot.restriction(ScopeType.ADVERTISER, PermissionLevel.VIEW);
		if (StringUtils.hasText(channelId)) {
			accessScope.require(ScopeType.CHANNEL, channelId, PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(appLinkId)) {
			requireAppLink(appLinkId, PermissionLevel.VIEW);
		}
		List<String> visibleLinks = visibleAppLinkIds(advertisers);
		if (isEmpty(channels) || (!advertisers.unrestricted() && visibleLinks.isEmpty())) {
			return emptyPage(page);
		}
		Page<OcpxDeductionRule> result = deductionMapper.selectPage(page,
				Wrappers.<OcpxDeductionRule>lambdaQuery()
					.in(!channels.unrestricted(), OcpxDeductionRule::getChannelId, channels.ids())
					.in(!advertisers.unrestricted(), OcpxDeductionRule::getAppLinkId, visibleLinks)
					.eq(StringUtils.hasText(channelId), OcpxDeductionRule::getChannelId, channelId)
					.eq(StringUtils.hasText(appLinkId), OcpxDeductionRule::getAppLinkId, appLinkId)
					.orderByDesc(OcpxDeductionRule::getCreateTime));
		return decorateDeductions(result);
	}

	public Map<String, Object> reportOptions() {
		Snapshot snapshot = accessScope.current();
		Restriction advertisers = snapshot.restriction(ScopeType.ADVERTISER, PermissionLevel.VIEW);
		Restriction channels = snapshot.restriction(ScopeType.CHANNEL, PermissionLevel.VIEW);
		List<OcpxAdvertiser> advertiserRows = isEmpty(advertisers) ? List.of()
				: advertiserMapper.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery()
					.in(!advertisers.unrestricted(), OcpxAdvertiser::getId, advertisers.ids())
					.orderByAsc(OcpxAdvertiser::getAdvertiserName));
		Map<String, String> advertiserNames = advertiserRows.stream()
			.collect(Collectors.toMap(OcpxAdvertiser::getId, OcpxAdvertiser::getAdvertiserName));
		Map<String, String> advertiserBidUrls = advertiserRows.stream()
			.collect(Collectors.toMap(OcpxAdvertiser::getId, OcpxAdvertiser::getBidUrl));
		List<OcpxAppLink> linkRows = isEmpty(advertisers) ? List.of()
				: appLinkMapper.selectList(Wrappers.<OcpxAppLink>lambdaQuery()
					.in(!advertisers.unrestricted(), OcpxAppLink::getAdvertiserId, advertisers.ids())
					.orderByAsc(OcpxAppLink::getLinkName));
		Map<String, String> linkNames = linkRows.stream()
			.collect(Collectors.toMap(OcpxAppLink::getId, OcpxAppLink::getLinkName));
		List<OcpxMonitorLink> monitorRows = isEmpty(advertisers) || isEmpty(channels) ? List.of()
				: monitorMapper.selectList(Wrappers.<OcpxMonitorLink>lambdaQuery()
					.in(!advertisers.unrestricted(), OcpxMonitorLink::getAdvertiserId, advertisers.ids())
					.in(!channels.unrestricted(), OcpxMonitorLink::getChannelId, channels.ids())
					.orderByAsc(OcpxMonitorLink::getMonitorName));
		List<OcpxChannel> channelRows = isEmpty(channels) ? List.of()
				: channelMapper.selectList(Wrappers.<OcpxChannel>lambdaQuery()
					.in(!channels.unrestricted(), OcpxChannel::getId, channels.ids())
					.orderByAsc(OcpxChannel::getChannelName));
		Map<String, String> channelNames = channelRows.stream()
			.collect(Collectors.toMap(OcpxChannel::getId, OcpxChannel::getChannelName));
		Map<String, com.yzytech.scx.ocpx.admin.dto.EventViews.BindingSummary> monitorSummaries = eventService
			.bindingSummaries(monitorRows.stream().map(OcpxMonitorLink::getId).toList());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("channels", channelRows.stream().map(this::channelView).toList());
		result.put("advertisers",
				advertiserRows.stream()
					.map(entity -> new CatalogViews.AdvertiserOption(entity.getId(), entity.getAdvertiserName()))
					.toList());
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
		OcpxChannel channel = requireChannel(request.channelId(), PermissionLevel.OPERATE);
		OcpxAppLink appLink = requireAppLink(request.appLinkId(), PermissionLevel.OPERATE);
		long duplicate = deductionMapper.selectCount(Wrappers.<OcpxDeductionRule>lambdaQuery()
			.eq(OcpxDeductionRule::getChannelId, request.channelId())
			.eq(OcpxDeductionRule::getAppLinkId, request.appLinkId())
			.ne(StringUtils.hasText(id), OcpxDeductionRule::getId, id));
		if (duplicate > 0) {
			throw new IllegalArgumentException("该渠道和应用链接已配置扣量规则");
		}
		OcpxDeductionRule entity = StringUtils.hasText(id) ? requireDeduction(id, PermissionLevel.OPERATE)
				: new OcpxDeductionRule();
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
		OcpxDeductionRule rule = requireDeduction(id, PermissionLevel.MANAGE);
		deductionMapper.deleteById(rule);
		configChangeService.deductionChanged(rule.getChannelId(), rule.getAppLinkId());
	}

	private IPage<CatalogViews.Monitor> decorateMonitors(Page<OcpxMonitorLink> source) {
		Map<String, String> channels = channelNames(
				source.getRecords().stream().map(OcpxMonitorLink::getChannelId).toList());
		Map<String, String> advertisers = advertiserNames(
				source.getRecords().stream().map(OcpxMonitorLink::getAdvertiserId).toList());
		Map<String, String> advertiserBidUrls = advertiserBidUrls(
				source.getRecords().stream().map(OcpxMonitorLink::getAdvertiserId).toList());
		Map<String, String> links = appLinkNames(
				source.getRecords().stream().map(OcpxMonitorLink::getAppLinkId).toList());
		Map<String, com.yzytech.scx.ocpx.admin.dto.EventViews.BindingSummary> summaries = eventService
			.bindingSummaries(source.getRecords().stream().map(OcpxMonitorLink::getId).toList());
		return source.convert(entity -> monitorView(entity, channels.get(entity.getChannelId()),
				advertisers.get(entity.getAdvertiserId()), links.get(entity.getAppLinkId()),
				advertiserBidUrls.get(entity.getAdvertiserId()), List.of(), summaries.get(entity.getId())));
	}

	private IPage<CatalogViews.Deduction> decorateDeductions(Page<OcpxDeductionRule> source) {
		Map<String, String> channels = channelNames(
				source.getRecords().stream().map(OcpxDeductionRule::getChannelId).toList());
		Map<String, String> links = appLinkNames(
				source.getRecords().stream().map(OcpxDeductionRule::getAppLinkId).toList());
		return source.convert(
				entity -> deductionView(entity, channels.get(entity.getChannelId()), links.get(entity.getAppLinkId())));
	}

	private Map<String, String> channelNames(List<String> ids) {
		return names(ids, channelMapper::selectBatchIds, OcpxChannel::getId, OcpxChannel::getChannelName);
	}

	private Map<String, String> advertiserNames(List<String> ids) {
		return names(ids, advertiserMapper::selectBatchIds, OcpxAdvertiser::getId, OcpxAdvertiser::getAdvertiserName);
	}

	private Map<String, String> advertiserBidUrls(List<String> ids) {
		return names(ids, advertiserMapper::selectBatchIds, OcpxAdvertiser::getId, OcpxAdvertiser::getBidUrl);
	}

	private Map<String, String> appLinkNames(List<String> ids) {
		return names(ids, appLinkMapper::selectBatchIds, OcpxAppLink::getId, OcpxAppLink::getLinkName);
	}

	private <T> Map<String, String> names(List<String> ids, Function<List<String>, List<T>> loader,
			Function<T, String> id, Function<T, String> name) {
		List<String> distinct = ids.stream().filter(StringUtils::hasText).distinct().toList();
		return distinct.isEmpty() ? Map.of()
				: loader.apply(distinct).stream().collect(Collectors.toMap(id, name, (left, right) -> left));
	}

	private OcpxChannel requireChannel(String id) {
		return requireChannel(id, PermissionLevel.VIEW);
	}

	private OcpxChannel requireChannel(String id, PermissionLevel permissionLevel) {
		accessScope.require(ScopeType.CHANNEL, id, permissionLevel);
		return require(channelMapper.selectById(id), "渠道不存在或无权访问");
	}

	private OcpxAdvertiser requireAdvertiser(String id) {
		return requireAdvertiser(id, PermissionLevel.VIEW);
	}

	private OcpxAdvertiser requireAdvertiser(String id, PermissionLevel permissionLevel) {
		accessScope.require(ScopeType.ADVERTISER, id, permissionLevel);
		return require(advertiserMapper.selectById(id), "广告主不存在或无权访问");
	}

	private OcpxAppLink requireAppLink(String id) {
		return requireAppLink(id, PermissionLevel.VIEW);
	}

	private OcpxAppLink requireAppLink(String id, PermissionLevel permissionLevel) {
		OcpxAppLink link = require(appLinkMapper.selectById(id), "应用链接不存在或无权访问");
		accessScope.require(ScopeType.ADVERTISER, link.getAdvertiserId(), permissionLevel);
		return link;
	}

	private OcpxMonitorLink requireMonitor(String id) {
		return requireMonitor(id, PermissionLevel.VIEW);
	}

	private OcpxMonitorLink requireMonitor(String id, PermissionLevel permissionLevel) {
		OcpxMonitorLink monitor = require(monitorMapper.selectById(id), "监测链接不存在或无权访问");
		accessScope.require(ScopeType.ADVERTISER, monitor.getAdvertiserId(), permissionLevel);
		accessScope.require(ScopeType.CHANNEL, monitor.getChannelId(), permissionLevel);
		return monitor;
	}

	private OcpxDeductionRule requireDeduction(String id) {
		return requireDeduction(id, PermissionLevel.VIEW);
	}

	private OcpxDeductionRule requireDeduction(String id, PermissionLevel permissionLevel) {
		OcpxDeductionRule rule = require(deductionMapper.selectById(id), "扣量规则不存在或无权访问");
		accessScope.require(ScopeType.CHANNEL, rule.getChannelId(), permissionLevel);
		requireAppLink(rule.getAppLinkId(), permissionLevel);
		return rule;
	}

	private <T> T require(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private void ensureUniqueChannel(String id, String name, String code) {
		if (channelMapper.selectCount(Wrappers.<OcpxChannel>lambdaQuery()
			.and(q -> q.eq(OcpxChannel::getChannelName, name.trim()).or().eq(OcpxChannel::getChannelCode, code.trim()))
			.ne(StringUtils.hasText(id), OcpxChannel::getId, id)) > 0) {
			throw new IllegalArgumentException("渠道名称或渠道编码已存在");
		}
	}

	private List<String> visibleAppLinkIds(Restriction advertisers) {
		if (advertisers.unrestricted()) {
			return List.of();
		}
		if (advertisers.ids().isEmpty()) {
			return List.of();
		}
		return appLinkMapper
			.selectList(Wrappers.<OcpxAppLink>lambdaQuery().in(OcpxAppLink::getAdvertiserId, advertisers.ids()))
			.stream()
			.map(OcpxAppLink::getId)
			.toList();
	}

	private boolean isEmpty(Restriction restriction) {
		return !restriction.unrestricted() && restriction.ids().isEmpty();
	}

	private <T> IPage<T> emptyPage(Page<?> requested) {
		Page<T> page = new Page<>(requested.getCurrent(), requested.getSize(), 0);
		page.setRecords(List.of());
		return page;
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

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private CatalogViews.Channel channelView(OcpxChannel entity) {
		return new CatalogViews.Channel(entity.getId(), entity.getChannelCode(), entity.getChannelName(),
				entity.getAdapterCode(), entity.getAuthType(), entity.getKeyVersion(), entity.getUri(),
				StringUtils.hasText(entity.getSecretKey()), entity.getMacroParams(), entity.getCallbackHostAllowlist(),
				entity.getStatus(), entity.getConfigVersion(), entity.getCreateTime(), entity.getUpdateTime());
	}

	private CatalogViews.AppLink appLinkView(OcpxAppLink entity, String advertiserName) {
		return new CatalogViews.AppLink(entity.getId(), entity.getAdvertiserId(), advertiserName,
				entity.getLinkPlatform(), entity.getLinkName(), entity.getLinkChannel(),
				maskedLinkUrl(entity, entity.getLinkUrl()), maskedLinkUrl(entity, entity.getDeeplink()),
				entity.getLinkExt(), entity.getLinkRemark(), entity.getCreateTime(), entity.getUpdateTime());
	}

	private String maskedLinkUrl(OcpxAppLink entity, String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		if (!"READY".equals(entity.getMappingStatus())) {
			return "历史地址（待重新录入并校验）";
		}
		OcpxUrlPolicy.Result checked = urlPolicy.validate(value);
		return StringUtils.hasText(checked.maskedUrl()) ? checked.maskedUrl() : "已保存（待安全复核）";
	}

	private CatalogViews.Monitor monitorView(OcpxMonitorLink entity, String channelName, String advertiserName,
			String appLinkName, String advertiserBidUrl, List<com.yzytech.scx.ocpx.admin.dto.EventViews.Binding> bindings,
			com.yzytech.scx.ocpx.admin.dto.EventViews.BindingSummary summary) {
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
				entity.getClickDedupWindowSeconds(), entity.getRouteStatus(),
				Boolean.TRUE.equals(entity.getRepublishRequired()), entity.getRepublishReason(),
				entity.getConfigVersion(), entity.getRtaMode(), entity.getBillingMode(), entity.getPublishedAt(),
				entity.getCreateTime(), entity.getUpdateTime(), bindings == null ? List.of() : bindings,
				summary == null ? (bindings == null ? 0 : bindings.size()) : summary.bindingCount(),
				summary == null ? null : summary.summary());
	}

	private CatalogViews.Deduction deductionView(OcpxDeductionRule entity, String channelName, String appLinkName) {
		return new CatalogViews.Deduction(entity.getId(), entity.getChannelId(), channelName, entity.getAppLinkId(),
				appLinkName, entity.getRatio(), entity.getConfigVersion(), entity.getCreateTime(),
				entity.getUpdateTime());
	}

}
