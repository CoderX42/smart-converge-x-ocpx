package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.ocpx.admin.dto.AccessScopeRequests;
import com.yzytech.scx.ocpx.admin.dto.AccessScopeViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxUserScope;
import com.yzytech.scx.ocpx.admin.entity.OcpxUserScopeRevision;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxUserScopeMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxUserScopeRevisionMapper;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.PermissionLevel;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.Restriction;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.ScopeType;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.Snapshot;
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
public class OcpxUserScopeService {

	private final OcpxUserScopeMapper scopeMapper;

	private final OcpxUserScopeRevisionMapper revisionMapper;

	private final OcpxSystemUserMapper userMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxChannelMapper channelMapper;

	private final OcpxMediaAccountMapper mediaAccountMapper;

	private final OcpxAppLinkMapper appLinkMapper;

	private final OcpxMonitorLinkMapper monitorMapper;

	private final OcpxAccessScope accessScope;

	public AccessScopeViews.UserScopes get(String userId) {
		accessScope.requireAll(PermissionLevel.VIEW);
		requireUser(userId);
		List<OcpxUserScope> rows = activeRows(userId);
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
			throw new BootBusinessException(HttpStatus.CONFLICT.value(), "OCPX 范围已被其他操作修改，请刷新后重试");
		}
		List<NormalizedScope> normalized = normalize(request.scopes());
		protectLastScopeAdministrator(userId, normalized);
		long nextVersion = currentVersion + 1;
		String actor = actor();
		scopeMapper.update(null,
				Wrappers.<OcpxUserScope>lambdaUpdate()
					.eq(OcpxUserScope::getUserId, userId)
					.eq(OcpxUserScope::getDeleted, false)
					.set(OcpxUserScope::getStatus, "DISABLED")
					.set(OcpxUserScope::getVersion, nextVersion)
					.set(OcpxUserScope::getUpdateBy, actor)
					.set(OcpxUserScope::getDeleted, true)
					.set(OcpxUserScope::getUpdateTime, LocalDateTime.now()));
		for (NormalizedScope item : normalized) {
			OcpxUserScope row = new OcpxUserScope();
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
				scopeMapper.selectList(Wrappers.<OcpxUserScope>lambdaQuery()
					.eq(OcpxUserScope::getUserId, userId)
					.eq(OcpxUserScope::getStatus, "ACTIVE")
					.orderByAsc(OcpxUserScope::getScopeType, OcpxUserScope::getScopeId)));
	}

	public AccessScopeViews.Options options() {
		accessScope.requireAll(PermissionLevel.VIEW);
		List<AccessScopeViews.Option> advertisers = advertiserMapper
			.selectList(Wrappers.<OcpxAdvertiser>lambdaQuery().orderByAsc(OcpxAdvertiser::getAdvertiserName))
			.stream()
			.map(row -> new AccessScopeViews.Option(ScopeType.ADVERTISER.name(), row.getId(), row.getAdvertiserName(),
					row.getStatus()))
			.toList();
		List<AccessScopeViews.Option> channels = channelMapper
			.selectList(Wrappers.<OcpxChannel>lambdaQuery().orderByAsc(OcpxChannel::getChannelName))
			.stream()
			.map(row -> new AccessScopeViews.Option(ScopeType.CHANNEL.name(), row.getId(), row.getChannelName(),
					row.getStatus()))
			.toList();
		boolean allManageable = accessScope.current().allows(ScopeType.ALL, null, PermissionLevel.MANAGE);
		List<AccessScopeViews.Option> mediaAccounts = mediaAccountMapper
			.selectList(Wrappers.<OcpxMediaAccount>lambdaQuery()
				.ne(OcpxMediaAccount::getStatus, "ARCHIVED")
				.orderByAsc(OcpxMediaAccount::getName))
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
		long existing = scopeMapper.selectCount(Wrappers.<OcpxUserScope>lambdaQuery()
			.eq(OcpxUserScope::getUserId, userId)
			.eq(OcpxUserScope::getScopeType, ScopeType.ADVERTISER.name())
			.eq(OcpxUserScope::getScopeId, advertiserId)
			.eq(OcpxUserScope::getStatus, "ACTIVE"));
		if (existing > 0) {
			return;
		}
		long nextVersion = revision(userId) + 1;
		OcpxUserScope row = new OcpxUserScope();
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
		long existing = scopeMapper.selectCount(Wrappers.<OcpxUserScope>lambdaQuery()
			.eq(OcpxUserScope::getUserId, userId)
			.eq(OcpxUserScope::getScopeType, ScopeType.MEDIA_ACCOUNT.name())
			.eq(OcpxUserScope::getScopeId, mediaAccountId)
			.eq(OcpxUserScope::getStatus, "ACTIVE"));
		if (existing > 0) {
			return;
		}
		long nextVersion = revision(userId) + 1;
		OcpxUserScope row = new OcpxUserScope();
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
				OcpxAdvertiser advertiser = advertiserMapper.selectById(scopeId);
				if (advertiser == null || !"ACTIVE".equals(advertiser.getStatus())) {
					throw new IllegalArgumentException("广告主不存在或已归档");
				}
			}
			case CHANNEL -> {
				OcpxChannel channel = channelMapper.selectById(scopeId);
				if (channel == null || !"ACTIVE".equals(channel.getStatus())) {
					throw new IllegalArgumentException("渠道不存在或已归档");
				}
			}
			case MEDIA_ACCOUNT -> {
				OcpxMediaAccount account = mediaAccountMapper.selectById(scopeId);
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
			throw new BootBusinessException(HttpStatus.CONFLICT.value(), "至少保留一个有效的 OCPX 范围管理员");
		}
	}

	private long activeScopeAdministratorCount(String userId, boolean excludeUser, LocalDateTime now) {
		return scopeMapper.selectCount(Wrappers.<OcpxUserScope>lambdaQuery()
			.eq(OcpxUserScope::getScopeType, ScopeType.ALL.name())
			.eq(OcpxUserScope::getPermissionLevel, PermissionLevel.MANAGE.name())
			.eq(OcpxUserScope::getStatus, "ACTIVE")
			.ne(excludeUser, OcpxUserScope::getUserId, userId)
			.eq(!excludeUser, OcpxUserScope::getUserId, userId)
			.and(query -> query.isNull(OcpxUserScope::getValidFrom).or().le(OcpxUserScope::getValidFrom, now))
			.and(query -> query.isNull(OcpxUserScope::getValidTo).or().gt(OcpxUserScope::getValidTo, now)));
	}

	private AccessScopeViews.UserScopes view(String userId, long version, List<OcpxUserScope> rows) {
		List<String> advertiserIds = ids(rows, ScopeType.ADVERTISER);
		List<String> channelIds = ids(rows, ScopeType.CHANNEL);
		List<String> mediaAccountIds = ids(rows, ScopeType.MEDIA_ACCOUNT);
		Map<String, String> advertiserNames = advertiserIds.isEmpty() ? Map.of()
				: advertiserMapper.selectBatchIds(advertiserIds)
					.stream()
					.collect(java.util.stream.Collectors.toMap(OcpxAdvertiser::getId, OcpxAdvertiser::getAdvertiserName));
		Map<String, String> channelNames = channelIds.isEmpty() ? Map.of()
				: channelMapper.selectBatchIds(channelIds)
					.stream()
					.collect(java.util.stream.Collectors.toMap(OcpxChannel::getId, OcpxChannel::getChannelName));
		Map<String, String> mediaAccountNames = mediaAccountIds.isEmpty() ? Map.of()
				: mediaAccountMapper.selectBatchIds(mediaAccountIds)
					.stream()
					.collect(java.util.stream.Collectors.toMap(OcpxMediaAccount::getId, OcpxMediaAccount::getName));
		List<AccessScopeViews.Scope> scopes = rows.stream()
			.map(row -> new AccessScopeViews.Scope(row.getId(), row.getScopeType(), row.getScopeId(),
					scopeName(row, advertiserNames, channelNames, mediaAccountNames), row.getPermissionLevel(), row.getValidFrom(),
					row.getValidTo(), row.getStatus()))
			.toList();
		return new AccessScopeViews.UserScopes(userId, version, scopes);
	}

	private List<String> ids(List<OcpxUserScope> rows, ScopeType type) {
		return rows.stream()
			.filter(row -> type.name().equals(row.getScopeType()))
			.map(OcpxUserScope::getScopeId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
	}

	private String scopeName(OcpxUserScope row, Map<String, String> advertiserNames, Map<String, String> channelNames,
			Map<String, String> mediaAccountNames) {
		return switch (ScopeType.valueOf(row.getScopeType())) {
			case ALL -> "全部 OCPX 对象";
			case ADVERTISER -> advertiserNames.getOrDefault(row.getScopeId(), "已失效广告主");
			case CHANNEL -> channelNames.getOrDefault(row.getScopeId(), "已失效渠道");
			case MEDIA_ACCOUNT -> mediaAccountNames.getOrDefault(row.getScopeId(), "已失效媒体账号");
		};
	}

	private List<OcpxUserScope> activeRows(String userId) {
		return scopeMapper.selectList(Wrappers.<OcpxUserScope>lambdaQuery()
			.eq(OcpxUserScope::getUserId, userId)
			.eq(OcpxUserScope::getStatus, "ACTIVE")
			.orderByAsc(OcpxUserScope::getScopeType, OcpxUserScope::getScopeId));
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
		OcpxUserScopeRevision revision = revisionMapper.selectById(userId);
		if (revision == null) {
			revision = new OcpxUserScopeRevision();
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
		LambdaQueryWrapper<OcpxAdvertiser> query = Wrappers.lambdaQuery();
		query.in(!restriction.unrestricted(), OcpxAdvertiser::getId, restriction.ids());
		return advertiserMapper.selectCount(query);
	}

	private long countChannels(Restriction restriction) {
		if (!restriction.unrestricted() && restriction.ids().isEmpty()) {
			return 0;
		}
		LambdaQueryWrapper<OcpxChannel> query = Wrappers.lambdaQuery();
		query.in(!restriction.unrestricted(), OcpxChannel::getId, restriction.ids());
		return channelMapper.selectCount(query);
	}

	private long countMediaAccounts(Restriction accounts, Restriction channels) {
		if ((!accounts.unrestricted() && accounts.ids().isEmpty())
				|| (!channels.unrestricted() && channels.ids().isEmpty())) {
			return 0;
		}
		LambdaQueryWrapper<OcpxMediaAccount> query = Wrappers.lambdaQuery();
		query.in(!accounts.unrestricted(), OcpxMediaAccount::getId, accounts.ids());
		query.in(!channels.unrestricted(), OcpxMediaAccount::getChannelId, channels.ids());
		return mediaAccountMapper.selectCount(query);
	}

	private long countAppLinks(Restriction advertisers) {
		if (!advertisers.unrestricted() && advertisers.ids().isEmpty()) {
			return 0;
		}
		LambdaQueryWrapper<OcpxAppLink> query = Wrappers.lambdaQuery();
		query.in(!advertisers.unrestricted(), OcpxAppLink::getAdvertiserId, advertisers.ids());
		return appLinkMapper.selectCount(query);
	}

	private long countMonitors(Restriction advertisers, Restriction channels, Restriction mediaAccounts,
			String routeStatus) {
		if ((!advertisers.unrestricted() && advertisers.ids().isEmpty())
				|| (!channels.unrestricted() && channels.ids().isEmpty())
				|| (!mediaAccounts.unrestricted() && mediaAccounts.ids().isEmpty())) {
			return 0;
		}
		LambdaQueryWrapper<OcpxMonitorLink> query = Wrappers.lambdaQuery();
		query.in(!advertisers.unrestricted(), OcpxMonitorLink::getAdvertiserId, advertisers.ids());
		query.in(!channels.unrestricted(), OcpxMonitorLink::getChannelId, channels.ids());
		query.in(!mediaAccounts.unrestricted(), OcpxMonitorLink::getMediaAccountId, mediaAccounts.ids());
		query.eq(StringUtils.hasText(routeStatus), OcpxMonitorLink::getRouteStatus, routeStatus);
		return monitorMapper.selectCount(query);
	}

	private record NormalizedScope(ScopeType type, String scopeId, PermissionLevel level, LocalDateTime validFrom,
			LocalDateTime validTo) {
	}

}
