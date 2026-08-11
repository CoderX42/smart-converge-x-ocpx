package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.entity.OcpxUserScope;
import com.yzytech.scx.ocpx.admin.mapper.OcpxUserScopeMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxUserScopeRevisionMapper;
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

class OcpxAccessScopeTest {

	private final OcpxUserScopeMapper scopeMapper = mock(OcpxUserScopeMapper.class);

	private final OcpxUserScopeRevisionMapper revisionMapper = mock(OcpxUserScopeRevisionMapper.class);

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

	private final OcpxAccessScope scope = new OcpxAccessScope(scopeMapper, revisionMapper, redisTemplate);

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
			assertThatThrownBy(() -> scope.require(OcpxAccessScope.ScopeType.CHANNEL, "channel-1",
					OcpxAccessScope.PermissionLevel.VIEW))
				.isInstanceOf(BootBusinessException.class);
		}
	}

	@Test
	void mediaAccountGrantDoesNotExpandToAnotherAccountOrItsChannel() {
		when(revisionMapper.selectVersion("user-1")).thenReturn(3L);
		when(scopeMapper.selectList(any()))
			.thenReturn(List.of(row("MEDIA_ACCOUNT", "media-1", "OPERATE")));

		try (MockedStatic<SecurityUtils> security = user("user-1")) {
			OcpxAccessScope.Snapshot snapshot = scope.current();
			assertThat(snapshot.allows(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, "media-1",
					OcpxAccessScope.PermissionLevel.OPERATE)).isTrue();
			assertThat(snapshot.allows(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, "media-2",
					OcpxAccessScope.PermissionLevel.VIEW)).isFalse();
			assertThat(snapshot.allows(OcpxAccessScope.ScopeType.CHANNEL, "channel-1",
					OcpxAccessScope.PermissionLevel.VIEW)).isFalse();
		}
	}

	@Test
	void allGrantCoversEveryObjectAtOrBelowItsLevel() {
		when(revisionMapper.selectVersion("admin")).thenReturn(2L);
		when(scopeMapper.selectList(any())).thenReturn(List.of(row("ALL", "*", "MANAGE")));

		try (MockedStatic<SecurityUtils> security = user("admin")) {
			OcpxAccessScope.Snapshot snapshot = scope.current();
			assertThat(
					snapshot.allows(OcpxAccessScope.ScopeType.ADVERTISER, "any", OcpxAccessScope.PermissionLevel.MANAGE))
				.isTrue();
			assertThat(snapshot.restriction(OcpxAccessScope.ScopeType.CHANNEL, OcpxAccessScope.PermissionLevel.VIEW)
				.unrestricted()).isTrue();
		}
	}

	@Test
	void databaseRevisionInvalidatesLocalCacheWithoutRedisNotification() {
		when(revisionMapper.selectVersion("user-1")).thenReturn(1L, 2L);
		when(scopeMapper.selectList(any())).thenReturn(List.of(row("CHANNEL", "channel-1", "VIEW")),
				List.of(row("CHANNEL", "channel-2", "VIEW")));

		assertThat(scope.forUser("user-1")
			.allows(OcpxAccessScope.ScopeType.CHANNEL, "channel-1", OcpxAccessScope.PermissionLevel.VIEW)).isTrue();
		assertThat(scope.forUser("user-1")
			.allows(OcpxAccessScope.ScopeType.CHANNEL, "channel-2", OcpxAccessScope.PermissionLevel.VIEW)).isTrue();
	}

	@Test
	void redisNotificationEvictsTheMatchingLocalEntry() {
		when(revisionMapper.selectVersion("user-1")).thenReturn(1L);
		when(scopeMapper.selectList(any())).thenReturn(List.of(row("CHANNEL", "channel-1", "VIEW")),
				List.of(row("CHANNEL", "channel-2", "VIEW")));
		Message message = mock(Message.class);
		when(message.getBody()).thenReturn("user-1".getBytes(StandardCharsets.UTF_8));

		assertThat(scope.forUser("user-1")
			.allows(OcpxAccessScope.ScopeType.CHANNEL, "channel-1", OcpxAccessScope.PermissionLevel.VIEW)).isTrue();
		scope.onMessage(message, null);
		assertThat(scope.forUser("user-1")
			.allows(OcpxAccessScope.ScopeType.CHANNEL, "channel-2", OcpxAccessScope.PermissionLevel.VIEW)).isTrue();
	}

	private MockedStatic<SecurityUtils> user(String userId) {
		BootUser user = new BootUser();
		user.setUserId(userId);
		MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
		security.when(SecurityUtils::getUser).thenReturn(user);
		return security;
	}

	private OcpxUserScope row(String type, String id, String level) {
		OcpxUserScope row = new OcpxUserScope();
		row.setScopeType(type);
		row.setScopeId(id);
		row.setPermissionLevel(level);
		row.setStatus("ACTIVE");
		return row;
	}

}
