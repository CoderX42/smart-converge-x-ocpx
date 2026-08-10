package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.adx.entity.AdxUserScope;
import com.yzytech.scx.adx.mapper.AdxUserScopeMapper;
import com.yzytech.scx.adx.mapper.AdxUserScopeRevisionMapper;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AdxAccessScope implements MessageListener {

	public static final String INVALIDATION_TOPIC = "scx:adx:scope:changed";

	private final AdxUserScopeMapper scopeMapper;

	private final AdxUserScopeRevisionMapper revisionMapper;

	private final StringRedisTemplate redisTemplate;

	private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

	public AdxAccessScope(AdxUserScopeMapper scopeMapper, AdxUserScopeRevisionMapper revisionMapper,
			StringRedisTemplate redisTemplate) {
		this.scopeMapper = scopeMapper;
		this.revisionMapper = revisionMapper;
		this.redisTemplate = redisTemplate;
	}

	public Snapshot current() {
		BootUser user = SecurityUtils.getUser();
		if (user == null || !StringUtils.hasText(user.getUserId())) {
			throw forbidden();
		}
		return forUser(user.getUserId());
	}

	public Snapshot forUser(String userId) {
		Long storedVersion = revisionMapper.selectVersion(userId);
		long version = storedVersion == null ? 0 : storedVersion;
		LocalDateTime now = LocalDateTime.now();
		CacheEntry existing = cache.get(userId);
		if (existing != null && existing.version() == version
				&& (existing.validUntil() == null || now.isBefore(existing.validUntil()))) {
			return existing.snapshot();
		}
		List<AdxUserScope> rows = scopeMapper.selectList(Wrappers.<AdxUserScope>lambdaQuery()
			.eq(AdxUserScope::getUserId, userId)
			.eq(AdxUserScope::getStatus, "ACTIVE")
			.orderByAsc(AdxUserScope::getScopeType, AdxUserScope::getScopeId));
		Snapshot snapshot = snapshot(version, rows, now);
		cache.put(userId, new CacheEntry(version, snapshot, nextBoundary(rows, now)));
		return snapshot;
	}

	public void require(ScopeType type, String scopeId, PermissionLevel permissionLevel) {
		if (!current().allows(type, scopeId, permissionLevel)) {
			throw forbidden();
		}
	}

	public void requireAll(PermissionLevel permissionLevel) {
		require(ScopeType.ALL, null, permissionLevel);
	}

	public String constrainAdvertiser(String requestedAdvertiserId) {
		Snapshot snapshot = current();
		if (StringUtils.hasText(requestedAdvertiserId)) {
			if (!snapshot.allows(ScopeType.ADVERTISER, requestedAdvertiserId, PermissionLevel.VIEW)) {
				throw forbidden();
			}
			return requestedAdvertiserId;
		}
		Restriction restriction = snapshot.restriction(ScopeType.ADVERTISER, PermissionLevel.VIEW);
		if (restriction.unrestricted()) {
			return null;
		}
		if (restriction.ids().size() == 1) {
			return restriction.ids().get(0);
		}
		if (restriction.ids().isEmpty()) {
			throw forbidden();
		}
		throw new IllegalStateException("当前账号具有多个广告主范围，调用方必须使用集合范围过滤");
	}

	public void invalidate(String userId) {
		evictLocal(userId);
		try {
			redisTemplate.convertAndSend(INVALIDATION_TOPIC, userId);
		}
		catch (RuntimeException error) {
			// Every read compares the database revision before reusing a local entry, so
			// a
			// failed notification cannot preserve stale authorization.
			log.warn("ADX scope invalidation notification failed for user {}", userId);
		}
	}

	public void evictLocal(String userId) {
		cache.remove(userId);
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String userId = new String(message.getBody(), StandardCharsets.UTF_8).trim();
		if (StringUtils.hasText(userId)) {
			evictLocal(userId);
		}
	}

	private Snapshot snapshot(long version, List<AdxUserScope> rows, LocalDateTime now) {
		Map<ScopeType, Map<String, PermissionLevel>> grants = new EnumMap<>(ScopeType.class);
		PermissionLevel allLevel = null;
		for (AdxUserScope row : rows) {
			if (!isEffective(row, now)) {
				continue;
			}
			ScopeType type = ScopeType.valueOf(row.getScopeType());
			PermissionLevel level = PermissionLevel.valueOf(row.getPermissionLevel());
			if (type == ScopeType.ALL) {
				allLevel = PermissionLevel.max(allLevel, level);
				continue;
			}
			grants.computeIfAbsent(type, ignored -> new HashMap<>())
				.merge(row.getScopeId(), level, PermissionLevel::max);
		}
		Map<ScopeType, Map<String, PermissionLevel>> immutable = new EnumMap<>(ScopeType.class);
		grants.forEach((type, values) -> immutable.put(type, Map.copyOf(values)));
		return new Snapshot(version, allLevel, Map.copyOf(immutable));
	}

	private boolean isEffective(AdxUserScope row, LocalDateTime now) {
		return (row.getValidFrom() == null || !now.isBefore(row.getValidFrom()))
				&& (row.getValidTo() == null || now.isBefore(row.getValidTo()));
	}

	private LocalDateTime nextBoundary(List<AdxUserScope> rows, LocalDateTime now) {
		LocalDateTime boundary = null;
		for (AdxUserScope row : rows) {
			for (LocalDateTime candidate : new LocalDateTime[] { row.getValidFrom(), row.getValidTo() }) {
				if (candidate != null && candidate.isAfter(now) && (boundary == null || candidate.isBefore(boundary))) {
					boundary = candidate;
				}
			}
		}
		return boundary;
	}

	private BootBusinessException forbidden() {
		return new BootBusinessException(HttpStatus.FORBIDDEN.value(), "对象不存在或无权访问");
	}

	public enum ScopeType {

		ALL, ADVERTISER, CHANNEL, MEDIA_ACCOUNT

	}

	public enum PermissionLevel {

		VIEW, OPERATE, MANAGE;

		public boolean atLeast(PermissionLevel required) {
			return ordinal() >= required.ordinal();
		}

		static PermissionLevel max(PermissionLevel left, PermissionLevel right) {
			if (left == null) {
				return right;
			}
			return left.ordinal() >= right.ordinal() ? left : right;
		}

	}

	public record Restriction(boolean unrestricted, List<String> ids) {
	}

	public record Snapshot(long version, PermissionLevel allLevel,
			Map<ScopeType, Map<String, PermissionLevel>> grants) {

		public boolean allows(ScopeType type, String id, PermissionLevel required) {
			if (allLevel != null && allLevel.atLeast(required)) {
				return true;
			}
			if (type == ScopeType.ALL || !StringUtils.hasText(id)) {
				return false;
			}
			PermissionLevel level = grants.getOrDefault(type, Map.of()).get(id);
			return level != null && level.atLeast(required);
		}

		public Restriction restriction(ScopeType type, PermissionLevel required) {
			if (allLevel != null && allLevel.atLeast(required)) {
				return new Restriction(true, List.of());
			}
			List<String> ids = new ArrayList<>();
			grants.getOrDefault(type, Map.of()).forEach((id, level) -> {
				if (level.atLeast(required)) {
					ids.add(id);
				}
			});
			ids.sort(String::compareTo);
			return new Restriction(false, List.copyOf(ids));
		}

	}

	private record CacheEntry(long version, Snapshot snapshot, LocalDateTime validUntil) {
	}

}
