package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.adx.dto.AccessScopeRequests;
import com.yzytech.scx.adx.dto.AccessScopeViews;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxAppLink;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxMediaAccount;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.entity.AdxUserScope;
import com.yzytech.scx.adx.entity.AdxUserScopeRevision;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxAppLinkMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxMediaAccountMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import com.yzytech.scx.adx.mapper.AdxSystemUserMapper;
import com.yzytech.scx.adx.mapper.AdxUserScopeMapper;
import com.yzytech.scx.adx.mapper.AdxUserScopeRevisionMapper;
import com.yzytech.scx.adx.service.AdxAccessScope.PermissionLevel;
import com.yzytech.scx.adx.service.AdxAccessScope.Restriction;
import com.yzytech.scx.adx.service.AdxAccessScope.ScopeType;
import com.yzytech.scx.adx.service.AdxAccessScope.Snapshot;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdxUserScopeService {

	private final AdxUserScopeMapper scopeMapper;

	private final AdxUserScopeRevisionMapper revisionMapper;

	private final AdxSystemUserMapper userMapper;

	private final AdxAdvertiserMapper advertiserMapper;

	private final AdxChannelMapper channelMapper;

	private final AdxMediaAccountMapper mediaAccountMapper;

	private final AdxAppLinkMapper appLinkMapper;

	private final AdxMonitorLinkMapper monitorMapper;

	private final AdxAccessScope accessScope;

	public AccessScopeViews.UserScopes get(String userId) {
		accessScope.requireAll(PermissionLevel.VIEW);
		requireUser(userId);
		List<AdxUserScope> rows = activeRows(userId);
		return view(userId, revision(userId), rows);
	}

	@Transactional
	public AccessScopeViews.UserScopes replace(String userId, AccessScopeRequests.Replace request) {
		accessScope.requireAll(PermissionLevel.MANAGE);
		lockUser(userId);
		// Serialize the cross-user last-administrator check to avoid write skew when
		// two administrators concurrently remove their own ALL/MANAGE grant.
		userMapper.lockScopeAdministration();
		long currentVersion = revision(userId);
		if (request.version() != currentVersion) {
			throw new BootBusinessException(HttpStatus.CONFLICT.value(), "ADX 范围已被其他操作修改，请刷新后重试");
		}
		List<NormalizedScope> normalized = normalize(request.scopes());
		protectLastScopeAdministrator(userId, normalized);
		long nextVersion = currentVersion + 1;
		String actor = actor();
		scopeMapper.update(null,
				Wrappers.<AdxUserScope>lambdaUpdate()
					.eq(AdxUserScope::getUserId, userId)
					.eq(AdxUserScope::getDeleted, false)
					.set(AdxUserScope::getStatus, "DISABLED")
					.set(AdxUserScope::getVersion, nextVersion)
					.set(AdxUserScope::getUpdateBy, actor)
					.set(AdxUserScope::getDeleted, true)
					.set(AdxUserScope::getUpdateTime, LocalDateTime.now()));
		for (NormalizedScope item : normalized) {
			AdxUserScope row = new AdxUserScope();
			row.setUserId(userId);
			row.setScopeType(item.type().name());
			row.setScopeId(item.scopeId());
			row.setPermissionLevel(item.level().name());
			row.setValidFrom(item.validFrom());
			row.setValidTo(item.validTo());
			row.setStatus("ACTIVE");
			row.setVersion(nextVersion);
			row.setCreateBy(actor);
			row.setUpdateBy(actor);
			row.setDeleted(false);
			scopeMapper.insert(row);
		}
		storeRevision(userId, nextVersion, actor);
		invalidateAfterCommit(userId);
		return view(userId, nextVersion,
				scopeMapper.selectList(Wrappers.<AdxUserScope>lambdaQuery()
					.eq(AdxUserScope::getUserId, userId)
					.eq(AdxUserScope::getStatus, "ACTIVE")
					.orderByAsc(AdxUserScope::getScopeType, AdxUserScope::getScopeId)));
	}

	public AccessScopeViews.Options options() {
		accessScope.requireAll(PermissionLevel.VIEW);
		List<AccessScopeViews.Option> advertisers = advertiserMapper
			.selectList(Wrappers.<AdxAdvertiser>lambdaQuery().orderByAsc(AdxAdvertiser::getAdvertiserName))
			.stream()
			.map(row -> new AccessScopeViews.Option(ScopeType.ADVERTISER.name(), row.getId(), row.getAdvertiserName(),
					row.getStatus()))
			.toList();
		List<AccessScopeViews.Option> channels = channelMapper
			.selectList(Wrappers.<AdxChannel>lambdaQuery().orderByAsc(AdxChannel::getChannelName))
			.stream()
			.map(row -> new AccessScopeViews.Option(ScopeType.CHANNEL.name(), row.getId(), row.getChannelName(),
					row.getStatus()))
			.toList();
		boolean allManageable = accessScope.current().allows(ScopeType.ALL, null, PermissionLevel.MANAGE);
		List<AccessScopeViews.Option> mediaAccounts = mediaAccountMapper
			.selectList(Wrappers.<AdxMediaAccount>lambdaQuery()
				.ne(AdxMediaAccount::getStatus, "ARCHIVED")
				.orderByAsc(AdxMediaAccount::getName))
			.stream()
			.map(row -> new AccessScopeViews.Option(ScopeType.MEDIA_ACCOUNT.name(), row.getId(), row.getName(),
					row.getStatus()))
			.toList();
		return new AccessScopeViews.Options(allManageable, advertisers, channels, mediaAccounts);
	}

	public AccessScopeViews.Impact impact(String userId) {
		accessScope.requireAll(PermissionLevel.VIEW);
		requireUser(userId);
		Snapshot snapshot = accessScope.forUser(userId);
		Restriction advertisers = snapshot.restriction(ScopeType.ADVERTISER, PermissionLevel.VIEW);
		Restriction channels = snapshot.restriction(ScopeType.CHANNEL, PermissionLevel.VIEW);
		Restriction mediaAccounts = snapshot.restriction(ScopeType.MEDIA_ACCOUNT, PermissionLevel.VIEW);
		long advertiserCount = countAdvertisers(advertisers);
		long channelCount = countChannels(channels);
		long mediaAccountCount = countMediaAccounts(mediaAccounts, channels);
		long appLinkCount = countAppLinks(advertisers);
		long monitorCount = countMonitors(advertisers, channels, mediaAccounts, null);
		long publishedMonitorCount = countMonitors(advertisers, channels, mediaAccounts, "PUBLISHED");
		return new AccessScopeViews.Impact(userId, snapshot.version(), advertiserCount, channelCount, mediaAccountCount,
				appLinkCount, monitorCount, publishedMonitorCount);
	}

	@Transactional
	public void ensureAdvertiserScope(String userId, String advertiserId) {
		lockUser(userId);
		long existing = scopeMapper.selectCount(Wrappers.<AdxUserScope>lambdaQuery()
			.eq(AdxUserScope::getUserId, userId)
			.eq(AdxUserScope::getScopeType, ScopeType.ADVERTISER.name())
			.eq(AdxUserScope::getScopeId, advertiserId)
			.eq(AdxUserScope::getStatus, "ACTIVE"));
		if (existing > 0) {
			return;
		}
		long nextVersion = revision(userId) + 1;
		AdxUserScope row = new AdxUserScope();
		row.setUserId(userId);
		row.setScopeType(ScopeType.ADVERTISER.name());
		row.setScopeId(advertiserId);
		row.setPermissionLevel(PermissionLevel.MANAGE.name());
		row.setStatus("ACTIVE");
		row.setVersion(nextVersion);
		row.setCreateBy(actor());
		row.setUpdateBy(actor());
		row.setDeleted(false);
		scopeMapper.insert(row);
		storeRevision(userId, nextVersion, actor());
		invalidateAfterCommit(userId);
	}

	@Transactional
	public void ensureMediaAccountScope(String userId, String mediaAccountId) {
		lockUser(userId);
		long existing = scopeMapper.selectCount(Wrappers.<AdxUserScope>lambdaQuery()
			.eq(AdxUserScope::getUserId, userId)
			.eq(AdxUserScope::getScopeType, ScopeType.MEDIA_ACCOUNT.name())
			.eq(AdxUserScope::getScopeId, mediaAccountId)
			.eq(AdxUserScope::getStatus, "ACTIVE"));
		if (existing > 0) {
			return;
		}
		long nextVersion = revision(userId) + 1;
		AdxUserScope row = new AdxUserScope();
		row.setUserId(userId);
		row.setScopeType(ScopeType.MEDIA_ACCOUNT.name());
		row.setScopeId(mediaAccountId);
		row.setPermissionLevel(PermissionLevel.MANAGE.name());
		row.setStatus("ACTIVE");
		row.setVersion(nextVersion);
		row.setCreateBy(actor());
		row.setUpdateBy(actor());
		row.setDeleted(false);
		scopeMapper.insert(row);
		storeRevision(userId, nextVersion, actor());
		invalidateAfterCommit(userId);
	}

	private List<NormalizedScope> normalize(List<AccessScopeRequests.Scope> requested) {
		List<NormalizedScope> result = new ArrayList<>();
		Set<String> unique = new HashSet<>();
		for (AccessScopeRequests.Scope item : requested) {
			ScopeType type;
			PermissionLevel level;
			try {
				type = ScopeType.valueOf(item.scopeType().trim().toUpperCase(Locale.ROOT));
				level = PermissionLevel.valueOf(item.permissionLevel().trim().toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException error) {
				throw new IllegalArgumentException("对象范围类型或权限级别无效");
			}
			String scopeId = StringUtils.hasText(item.scopeId()) ? item.scopeId().trim() : null;
			if (type == ScopeType.ALL && scopeId != null) {
				throw new IllegalArgumentException("ALL 范围不能指定对象 ID");
			}
			if (type != ScopeType.ALL && scopeId == null) {
				throw new IllegalArgumentException("具体对象范围必须指定对象 ID");
			}
			if (item.validFrom() != null && item.validTo() != null && !item.validFrom().isBefore(item.validTo())) {
				throw new IllegalArgumentException("授权开始时间必须早于结束时间");
			}
			validateTarget(type, scopeId);
			if (!unique.add(type.name() + ":" + (scopeId == null ? "" : scopeId))) {
				throw new IllegalArgumentException("对象范围不能重复");
			}
			result.add(new NormalizedScope(type, scopeId, level, item.validFrom(), item.validTo()));
		}
		if (result.size() > 1 && result.stream().anyMatch(item -> item.type() == ScopeType.ALL)) {
			throw new IllegalArgumentException("ALL 范围不能与具体对象范围同时配置");
		}
		return result;
	}

	private void validateTarget(ScopeType type, String scopeId) {
		switch (type) {
			case ALL -> {
			}
			case ADVERTISER -> {
				AdxAdvertiser advertiser = advertiserMapper.selectById(scopeId);
				if (advertiser == null || !"ACTIVE".equals(advertiser.getStatus())) {
					throw new IllegalArgumentException("广告主不存在或已归档");
				}
			}
			case CHANNEL -> {
				AdxChannel channel = channelMapper.selectById(scopeId);
				if (channel == null || !"ACTIVE".equals(channel.getStatus())) {
					throw new IllegalArgumentException("渠道不存在或已归档");
				}
			}
			case MEDIA_ACCOUNT -> {
				AdxMediaAccount account = mediaAccountMapper.selectById(scopeId);
				if (account == null || "ARCHIVED".equals(account.getStatus())) {
					throw new IllegalArgumentException("媒体账号不存在或已归档");
				}
			}
		}
	}

	private void protectLastScopeAdministrator(String userId, List<NormalizedScope> normalized) {
		LocalDateTime now = LocalDateTime.now();
		boolean remainsAdministrator = normalized.stream()
			.anyMatch(item -> item.type() == ScopeType.ALL && item.level() == PermissionLevel.MANAGE
					&& (item.validFrom() == null || !now.isBefore(item.validFrom()))
					&& (item.validTo() == null || now.isBefore(item.validTo())));
		if (remainsAdministrator || activeScopeAdministratorCount(userId, false, now) == 0) {
			return;
		}
		if (activeScopeAdministratorCount(userId, true, now) == 0) {
			throw new BootBusinessException(HttpStatus.CONFLICT.value(), "至少保留一个有效的 ADX 范围管理员");
		}
	}

	private long activeScopeAdministratorCount(String userId, boolean excludeUser, LocalDateTime now) {
		return scopeMapper.selectCount(Wrappers.<AdxUserScope>lambdaQuery()
			.eq(AdxUserScope::getScopeType, ScopeType.ALL.name())
			.eq(AdxUserScope::getPermissionLevel, PermissionLevel.MANAGE.name())
			.eq(AdxUserScope::getStatus, "ACTIVE")
			.ne(excludeUser, AdxUserScope::getUserId, userId)
			.eq(!excludeUser, AdxUserScope::getUserId, userId)
			.and(query -> query.isNull(AdxUserScope::getValidFrom).or().le(AdxUserScope::getValidFrom, now))
			.and(query -> query.isNull(AdxUserScope::getValidTo).or().gt(AdxUserScope::getValidTo, now)));
	}

	private AccessScopeViews.UserScopes view(String userId, long version, List<AdxUserScope> rows) {
		List<String> advertiserIds = ids(rows, ScopeType.ADVERTISER);
		List<String> channelIds = ids(rows, ScopeType.CHANNEL);
		List<String> mediaAccountIds = ids(rows, ScopeType.MEDIA_ACCOUNT);
		Map<String, String> advertiserNames = advertiserIds.isEmpty() ? Map.of()
				: advertiserMapper.selectBatchIds(advertiserIds)
					.stream()
					.collect(java.util.stream.Collectors.toMap(AdxAdvertiser::getId, AdxAdvertiser::getAdvertiserName));
		Map<String, String> channelNames = channelIds.isEmpty() ? Map.of()
				: channelMapper.selectBatchIds(channelIds)
					.stream()
					.collect(java.util.stream.Collectors.toMap(AdxChannel::getId, AdxChannel::getChannelName));
		Map<String, String> mediaAccountNames = mediaAccountIds.isEmpty() ? Map.of()
				: mediaAccountMapper.selectBatchIds(mediaAccountIds)
					.stream()
					.collect(java.util.stream.Collectors.toMap(AdxMediaAccount::getId, AdxMediaAccount::getName));
		List<AccessScopeViews.Scope> scopes = rows.stream()
			.map(row -> new AccessScopeViews.Scope(row.getId(), row.getScopeType(), row.getScopeId(),
					scopeName(row, advertiserNames, channelNames, mediaAccountNames), row.getPermissionLevel(), row.getValidFrom(),
					row.getValidTo(), row.getStatus()))
			.toList();
		return new AccessScopeViews.UserScopes(userId, version, scopes);
	}

	private List<String> ids(List<AdxUserScope> rows, ScopeType type) {
		return rows.stream()
			.filter(row -> type.name().equals(row.getScopeType()))
			.map(AdxUserScope::getScopeId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
	}

	private String scopeName(AdxUserScope row, Map<String, String> advertiserNames, Map<String, String> channelNames,
			Map<String, String> mediaAccountNames) {
		return switch (ScopeType.valueOf(row.getScopeType())) {
			case ALL -> "全部 ADX 对象";
			case ADVERTISER -> advertiserNames.getOrDefault(row.getScopeId(), "已失效广告主");
			case CHANNEL -> channelNames.getOrDefault(row.getScopeId(), "已失效渠道");
			case MEDIA_ACCOUNT -> mediaAccountNames.getOrDefault(row.getScopeId(), "已失效媒体账号");
		};
	}

	private List<AdxUserScope> activeRows(String userId) {
		return scopeMapper.selectList(Wrappers.<AdxUserScope>lambdaQuery()
			.eq(AdxUserScope::getUserId, userId)
			.eq(AdxUserScope::getStatus, "ACTIVE")
			.orderByAsc(AdxUserScope::getScopeType, AdxUserScope::getScopeId));
	}

	private void requireUser(String userId) {
		SysUser user = userMapper.selectById(userId);
		if (user == null) {
			throw new IllegalArgumentException("用户不存在或已删除");
		}
	}

	private void lockUser(String userId) {
		if (!StringUtils.hasText(userMapper.lockActiveUser(userId))) {
			throw new IllegalArgumentException("用户不存在或已删除");
		}
	}

	private long revision(String userId) {
		Long version = revisionMapper.selectVersion(userId);
		return version == null ? 0 : version;
	}

	private void storeRevision(String userId, long version, String actor) {
		AdxUserScopeRevision revision = revisionMapper.selectById(userId);
		if (revision == null) {
			revision = new AdxUserScopeRevision();
			revision.setUserId(userId);
			revision.setVersion(version);
			revision.setUpdateBy(actor);
			revision.setUpdateTime(LocalDateTime.now());
			revisionMapper.insert(revision);
		}
		else {
			revision.setVersion(version);
			revision.setUpdateBy(actor);
			revision.setUpdateTime(LocalDateTime.now());
			revisionMapper.updateById(revision);
		}
	}

	private void invalidateAfterCommit(String userId) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			accessScope.invalidate(userId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				accessScope.invalidate(userId);
			}
		});
	}

	private String actor() {
		String actor = SecurityUtils.getUserName();
		return StringUtils.hasText(actor) ? actor : "SYSTEM";
	}

	private long countAdvertisers(Restriction restriction) {
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			return 0;
		}
		LambdaQueryWrapper<AdxAdvertiser> query = Wrappers.lambdaQuery();
		query.in(!restriction.unrestricted(), AdxAdvertiser::getId, restriction.ids());
		return advertiserMapper.selectCount(query);
	}

	private long countChannels(Restriction restriction) {
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			return 0;
		}
		LambdaQueryWrapper<AdxChannel> query = Wrappers.lambdaQuery();
		query.in(!restriction.unrestricted(), AdxChannel::getId, restriction.ids());
		return channelMapper.selectCount(query);
	}

	private long countMediaAccounts(Restriction accounts, Restriction channels) {
		if ((!accounts.unrestricted() && accounts.ids().isEmpty())
				|| (!channels.unrestricted() && channels.ids().isEmpty())) {
			return 0;
		}
		LambdaQueryWrapper<AdxMediaAccount> query = Wrappers.lambdaQuery();
		query.in(!accounts.unrestricted(), AdxMediaAccount::getId, accounts.ids());
		query.in(!channels.unrestricted(), AdxMediaAccount::getChannelId, channels.ids());
		return mediaAccountMapper.selectCount(query);
	}

	private long countAppLinks(Restriction advertisers) {
		if (!advertisers.unrestricted() && advertisers.ids().isEmpty()) {
			return 0;
		}
		LambdaQueryWrapper<AdxAppLink> query = Wrappers.lambdaQuery();
		query.in(!advertisers.unrestricted(), AdxAppLink::getAdvertiserId, advertisers.ids());
		return appLinkMapper.selectCount(query);
	}

	private long countMonitors(Restriction advertisers, Restriction channels, Restriction mediaAccounts,
			String routeStatus) {
		if ((!advertisers.unrestricted() && advertisers.ids().isEmpty())
				|| (!channels.unrestricted() && channels.ids().isEmpty())
				|| (!mediaAccounts.unrestricted() && mediaAccounts.ids().isEmpty())) {
			return 0;
		}
		LambdaQueryWrapper<AdxMonitorLink> query = Wrappers.lambdaQuery();
		query.in(!advertisers.unrestricted(), AdxMonitorLink::getAdvertiserId, advertisers.ids());
		query.in(!channels.unrestricted(), AdxMonitorLink::getChannelId, channels.ids());
		query.in(!mediaAccounts.unrestricted(), AdxMonitorLink::getMediaAccountId, mediaAccounts.ids());
		query.eq(StringUtils.hasText(routeStatus), AdxMonitorLink::getRouteStatus, routeStatus);
		return monitorMapper.selectCount(query);
	}

	private record NormalizedScope(ScopeType type, String scopeId, PermissionLevel level, LocalDateTime validFrom,
			LocalDateTime validTo) {
	}

}
