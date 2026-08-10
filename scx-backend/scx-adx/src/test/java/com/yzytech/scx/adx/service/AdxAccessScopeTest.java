package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.entity.AdxUserScope;
import com.yzytech.scx.adx.mapper.AdxUserScopeMapper;
import com.yzytech.scx.adx.mapper.AdxUserScopeRevisionMapper;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AdxAccessScopeTest {

	private final AdxUserScopeMapper scopeMapper = mock(AdxUserScopeMapper.class);

	private final AdxUserScopeRevisionMapper revisionMapper = mock(AdxUserScopeRevisionMapper.class);

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

	private final AdxAccessScope scope = new AdxAccessScope(scopeMapper, revisionMapper, redisTemplate);

	@Test
	void explicitScopeCannotBeExpandedByRequestParameter() {
		when(revisionMapper.selectVersion("user-1")).thenReturn(1L);
		when(scopeMapper.selectList(any())).thenReturn(List.of(row("ADVERTISER", "advertiser-1", "VIEW")));

		try (MockedStatic<SecurityUtils> security = user("user-1")) {
			assertThat(scope.constrainAdvertiser("advertiser-1")).isEqualTo("advertiser-1");
			assertThatThrownBy(() -> scope.constrainAdvertiser("advertiser-2"))
				.isInstanceOf(BootBusinessException.class);
		}
	}

	@Test
	void missingRowsFailClosed() {
		when(revisionMapper.selectVersion("user-1")).thenReturn(null);
		when(scopeMapper.selectList(any())).thenReturn(List.of());

		try (MockedStatic<SecurityUtils> security = user("user-1")) {
			assertThatThrownBy(() -> scope.require(AdxAccessScope.ScopeType.CHANNEL, "channel-1",
					AdxAccessScope.PermissionLevel.VIEW))
				.isInstanceOf(BootBusinessException.class);
		}
	}

	@Test
	void mediaAccountGrantDoesNotExpandToAnotherAccountOrItsChannel() {
		when(revisionMapper.selectVersion("user-1")).thenReturn(3L);
		when(scopeMapper.selectList(any()))
			.thenReturn(List.of(row("MEDIA_ACCOUNT", "media-1", "OPERATE")));

		try (MockedStatic<SecurityUtils> security = user("user-1")) {
			AdxAccessScope.Snapshot snapshot = scope.current();
			assertThat(snapshot.allows(AdxAccessScope.ScopeType.MEDIA_ACCOUNT, "media-1",
					AdxAccessScope.PermissionLevel.OPERATE)).isTrue();
			assertThat(snapshot.allows(AdxAccessScope.ScopeType.MEDIA_ACCOUNT, "media-2",
					AdxAccessScope.PermissionLevel.VIEW)).isFalse();
			assertThat(snapshot.allows(AdxAccessScope.ScopeType.CHANNEL, "channel-1",
					AdxAccessScope.PermissionLevel.VIEW)).isFalse();
		}
	}

	@Test
	void allGrantCoversEveryObjectAtOrBelowItsLevel() {
		when(revisionMapper.selectVersion("admin")).thenReturn(2L);
		when(scopeMapper.selectList(any())).thenReturn(List.of(row("ALL", "*", "MANAGE")));

		try (MockedStatic<SecurityUtils> security = user("admin")) {
			AdxAccessScope.Snapshot snapshot = scope.current();
			assertThat(
					snapshot.allows(AdxAccessScope.ScopeType.ADVERTISER, "any", AdxAccessScope.PermissionLevel.MANAGE))
				.isTrue();
			assertThat(snapshot.restriction(AdxAccessScope.ScopeType.CHANNEL, AdxAccessScope.PermissionLevel.VIEW)
				.unrestricted()).isTrue();
		}
	}

	@Test
	void databaseRevisionInvalidatesLocalCacheWithoutRedisNotification() {
		when(revisionMapper.selectVersion("user-1")).thenReturn(1L, 2L);
		when(scopeMapper.selectList(any())).thenReturn(List.of(row("CHANNEL", "channel-1", "VIEW")),
				List.of(row("CHANNEL", "channel-2", "VIEW")));

		assertThat(scope.forUser("user-1")
			.allows(AdxAccessScope.ScopeType.CHANNEL, "channel-1", AdxAccessScope.PermissionLevel.VIEW)).isTrue();
		assertThat(scope.forUser("user-1")
			.allows(AdxAccessScope.ScopeType.CHANNEL, "channel-2", AdxAccessScope.PermissionLevel.VIEW)).isTrue();
	}

	@Test
	void redisNotificationEvictsTheMatchingLocalEntry() {
		when(revisionMapper.selectVersion("user-1")).thenReturn(1L);
		when(scopeMapper.selectList(any())).thenReturn(List.of(row("CHANNEL", "channel-1", "VIEW")),
				List.of(row("CHANNEL", "channel-2", "VIEW")));
		Message message = mock(Message.class);
		when(message.getBody()).thenReturn("user-1".getBytes(StandardCharsets.UTF_8));

		assertThat(scope.forUser("user-1")
			.allows(AdxAccessScope.ScopeType.CHANNEL, "channel-1", AdxAccessScope.PermissionLevel.VIEW)).isTrue();
		scope.onMessage(message, null);
		assertThat(scope.forUser("user-1")
			.allows(AdxAccessScope.ScopeType.CHANNEL, "channel-2", AdxAccessScope.PermissionLevel.VIEW)).isTrue();
	}

	private MockedStatic<SecurityUtils> user(String userId) {
		BootUser user = new BootUser();
		user.setUserId(userId);
		MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
		security.when(SecurityUtils::getUser).thenReturn(user);
		return security;
	}

	private AdxUserScope row(String type, String id, String level) {
		AdxUserScope row = new AdxUserScope();
		row.setScopeType(type);
		row.setScopeId(id);
		row.setPermissionLevel(level);
		row.setStatus("ACTIVE");
		return row;
	}

}
