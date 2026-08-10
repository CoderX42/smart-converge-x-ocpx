package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yzytech.scx.adx.dto.AccessScopeRequests;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxMediaAccount;
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
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdxUserScopeServiceTest {

	@BeforeAll
	static void initializeTableMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "scope-test"),
				AdxUserScope.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "media-scope-test"),
				AdxMediaAccount.class);
	}

	private final AdxUserScopeMapper scopeMapper = mock(AdxUserScopeMapper.class);

	private final AdxUserScopeRevisionMapper revisionMapper = mock(AdxUserScopeRevisionMapper.class);

	private final AdxSystemUserMapper userMapper = mock(AdxSystemUserMapper.class);

	private final AdxAdvertiserMapper advertiserMapper = mock(AdxAdvertiserMapper.class);

	private final AdxChannelMapper channelMapper = mock(AdxChannelMapper.class);

	private final AdxMediaAccountMapper mediaAccountMapper = mock(AdxMediaAccountMapper.class);

	private final AdxAppLinkMapper appLinkMapper = mock(AdxAppLinkMapper.class);

	private final AdxMonitorLinkMapper monitorMapper = mock(AdxMonitorLinkMapper.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxUserScopeService service = new AdxUserScopeService(scopeMapper, revisionMapper, userMapper,
			advertiserMapper, channelMapper, mediaAccountMapper, appLinkMapper, monitorMapper, accessScope);

	@Test
	void rejectsAStaleWholeSetVersionBeforeChangingRows() {
		when(userMapper.lockActiveUser("user-1")).thenReturn("user-1");
		when(revisionMapper.selectVersion("user-1")).thenReturn(4L);

		assertThatThrownBy(() -> service.replace("user-1", new AccessScopeRequests.Replace(3L, List.of())))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("刷新后重试");
		verify(scopeMapper, never()).insert(any(AdxUserScope.class));
	}

	@Test
	void rejectsDuplicateAndMissingMediaAccountScopes() {
		when(userMapper.lockActiveUser("user-1")).thenReturn("user-1");
		when(revisionMapper.selectVersion("user-1")).thenReturn(1L);
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setStatus("ACTIVE");
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		AccessScopeRequests.Scope duplicate = new AccessScopeRequests.Scope("ADVERTISER", "advertiser-1", "VIEW", null,
				null);

		assertThatThrownBy(
				() -> service.replace("user-1", new AccessScopeRequests.Replace(1L, List.of(duplicate, duplicate))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("对象范围不能重复");
		assertThatThrownBy(() -> service.replace("user-1",
				new AccessScopeRequests.Replace(1L,
						List.of(new AccessScopeRequests.Scope("MEDIA_ACCOUNT", "account-1", "VIEW", null, null)))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不存在或已归档");
	}

	@Test
	void acceptsAnActiveMediaAccountScope() {
		when(userMapper.lockActiveUser("user-1")).thenReturn("user-1");
		when(revisionMapper.selectVersion("user-1")).thenReturn(1L);
		when(scopeMapper.selectList(any())).thenReturn(List.of());
		AdxMediaAccount account = new AdxMediaAccount();
		account.setId("account-1");
		account.setStatus("ACTIVE");
		when(mediaAccountMapper.selectById("account-1")).thenReturn(account);

		var result = service.replace("user-1",
				new AccessScopeRequests.Replace(1L,
						List.of(new AccessScopeRequests.Scope("MEDIA_ACCOUNT", "account-1", "OPERATE", null, null))));

		assertThat(result.version()).isEqualTo(2L);
		verify(scopeMapper).insert(any(AdxUserScope.class));
		verify(accessScope).invalidate("user-1");
	}

	@Test
	void refusesToRemoveTheLastEffectiveScopeAdministrator() {
		when(userMapper.lockActiveUser("admin-1")).thenReturn("admin-1");
		when(revisionMapper.selectVersion("admin-1")).thenReturn(7L);
		when(scopeMapper.selectCount(any())).thenReturn(1L, 0L);

		assertThatThrownBy(() -> service.replace("admin-1", new AccessScopeRequests.Replace(7L, List.of())))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("至少保留一个");
		verify(userMapper).lockScopeAdministration();
		verify(scopeMapper, never()).insert(any(AdxUserScope.class));
	}

	@Test
	void replacesTheSetAdvancesRevisionAndInvalidatesAuthorizationCache() {
		when(userMapper.lockActiveUser("user-1")).thenReturn("user-1");
		when(revisionMapper.selectVersion("user-1")).thenReturn(2L);
		AdxUserScopeRevision revision = new AdxUserScopeRevision();
		revision.setUserId("user-1");
		revision.setVersion(2L);
		when(revisionMapper.selectById("user-1")).thenReturn(revision);
		when(scopeMapper.selectList(any())).thenReturn(List.of());
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setStatus("ACTIVE");
		AdxChannel channel = new AdxChannel();
		channel.setId("channel-1");
		channel.setStatus("ACTIVE");
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		when(channelMapper.selectById("channel-1")).thenReturn(channel);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserId).thenReturn("admin");
			var result = service.replace("user-1",
					new AccessScopeRequests.Replace(2L,
							List.of(new AccessScopeRequests.Scope("ADVERTISER", "advertiser-1", "OPERATE", null, null),
									new AccessScopeRequests.Scope("CHANNEL", "channel-1", "VIEW", null, null))));

			assertThat(result.version()).isEqualTo(3L);
		}
		verify(scopeMapper, org.mockito.Mockito.times(2)).insert(any(AdxUserScope.class));
		verify(revisionMapper).updateById(revision);
		assertThat(revision.getVersion()).isEqualTo(3L);
		verify(accessScope).invalidate("user-1");
	}

}
