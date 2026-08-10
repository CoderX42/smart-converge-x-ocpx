package com.yzytech.scx.adx.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yzytech.scx.adx.dto.MediaAccountRequests;
import com.yzytech.scx.adx.dto.MediaAccountViews;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxMediaAccount;
import com.yzytech.scx.adx.entity.AdxMediaAccountChangeAudit;
import com.yzytech.scx.adx.entity.AdxMediaAccountOwner;
import com.yzytech.scx.adx.entity.AdxMediaCredential;
import com.yzytech.scx.adx.entity.AdxMediaCredentialRevision;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxIntegrationDocumentMapper;
import com.yzytech.scx.adx.mapper.AdxMediaAccountChangeAuditMapper;
import com.yzytech.scx.adx.mapper.AdxMediaAccountMapper;
import com.yzytech.scx.adx.mapper.AdxMediaAccountOwnerMapper;
import com.yzytech.scx.adx.mapper.AdxMediaCredentialMapper;
import com.yzytech.scx.adx.mapper.AdxMediaCredentialRevisionMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import com.yzytech.scx.adx.mapper.AdxSystemUserMapper;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdxMediaAccountServiceTest {

	@BeforeAll
	static void initializeTableMetadata() {
		for (Class<?> entity : new Class<?>[] { AdxMediaAccount.class, AdxMediaCredential.class,
				AdxMediaCredentialRevision.class, AdxMediaAccountOwner.class, AdxMonitorLink.class }) {
			TableInfoHelper
				.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), entity.getSimpleName()), entity);
		}
	}

	private final AdxMediaAccountMapper accountMapper = mock(AdxMediaAccountMapper.class);

	private final AdxMediaCredentialMapper credentialMapper = mock(AdxMediaCredentialMapper.class);

	private final AdxMediaCredentialRevisionMapper revisionMapper = mock(AdxMediaCredentialRevisionMapper.class);

	private final AdxMediaAccountOwnerMapper ownerMapper = mock(AdxMediaAccountOwnerMapper.class);

	private final AdxMediaAccountChangeAuditMapper auditMapper = mock(AdxMediaAccountChangeAuditMapper.class);

	private final AdxChannelMapper channelMapper = mock(AdxChannelMapper.class);

	private final AdxIntegrationDocumentMapper documentMapper = mock(AdxIntegrationDocumentMapper.class);

	private final AdxMonitorLinkMapper monitorMapper = mock(AdxMonitorLinkMapper.class);

	private final AdxSystemUserMapper userMapper = mock(AdxSystemUserMapper.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxUserScopeService userScopeService = mock(AdxUserScopeService.class);

	private final AdxConfigChangeService configChangeService = mock(AdxConfigChangeService.class);

	private final AdxAdapterService adapterService = mock(AdxAdapterService.class);

	private final ProtocolCrypto crypto = mock(ProtocolCrypto.class);

	private AdxMediaAccountService service;

	@BeforeEach
	void setUp() {
		service = new AdxMediaAccountService(accountMapper, credentialMapper, revisionMapper, ownerMapper, auditMapper,
				channelMapper, documentMapper, monitorMapper, userMapper, accessScope, userScopeService,
				configChangeService, adapterService, crypto);
		when(ownerMapper.selectCount(any())).thenReturn(0L);
		when(monitorMapper.selectCount(any())).thenReturn(0L);
	}

	@Test
	void createsWriteOnlyEncryptedCredentialWithoutResponseOrAuditLeakage() {
		AdxMediaAccount account = account();
		when(accountMapper.selectById("media-1")).thenReturn(account);
		when(accountMapper.selectOne(any())).thenReturn(account);
		when(credentialMapper.insert(any(AdxMediaCredential.class))).thenAnswer(invocation -> {
			((AdxMediaCredential) invocation.getArgument(0)).setId("credential-1");
			return 1;
		});
		when(crypto.encrypt("owner@example.invalid")).thenReturn("encrypted-identifier");
		when(crypto.encrypt("local-secret-value")).thenReturn("encrypted-secret");
		when(crypto.blindIndex("media-login:media-1", "owner@example.invalid")).thenReturn("blind-index");
		when(configChangeService.mediaAccountChanged(any(), any())).thenReturn(2);

		try (MockedStatic<StpUtil> safe = mockStatic(StpUtil.class);
				MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			MediaAccountViews.Credential result = service.createCredential("media-1",
					new MediaAccountRequests.CreateCredential("EMAIL", "Owner@Example.Invalid", "local-secret-value",
							"primary"));

			assertThat(result.maskedIdentifier()).isEqualTo("ow***@example.invalid");
			assertThat(result.secretConfigured()).isTrue();
			safe.verify(StpUtil::checkSafe);
		}

		ArgumentCaptor<AdxMediaCredential> stored = ArgumentCaptor.forClass(AdxMediaCredential.class);
		verify(credentialMapper).insert(stored.capture());
		assertThat(stored.getValue().getLoginIdentifierCiphertext()).isEqualTo("encrypted-identifier");
		assertThat(stored.getValue().getLoginIdentifierHash()).isEqualTo("blind-index");
		assertThat(stored.getValue().getStatus()).isEqualTo("PENDING");
		ArgumentCaptor<AdxMediaCredentialRevision> revision = ArgumentCaptor.forClass(AdxMediaCredentialRevision.class);
		verify(revisionMapper).insert(revision.capture());
		assertThat(revision.getValue().getSecretCiphertext()).isEqualTo("encrypted-secret");
		assertThat(Arrays.stream(MediaAccountViews.Credential.class.getRecordComponents())
			.map(component -> component.getName())).doesNotContain("loginIdentifier", "loginIdentifierCiphertext",
					"secretInput", "secretCiphertext", "credentialRef");
		ArgumentCaptor<AdxMediaAccountChangeAudit> audit = ArgumentCaptor.forClass(AdxMediaAccountChangeAudit.class);
		verify(auditMapper).insert(audit.capture());
		assertThat(audit.getValue().getChangeSummary()).doesNotContain("owner@example", "local-secret-value");
	}

	@Test
	void configurationPreflightNeverDecryptsOrCallsMediaNetwork() {
		AdxMediaAccount account = account();
		account.setStatus("DISABLED");
		account.setDefaultCredentialId("credential-1");
		AdxChannel channel = new AdxChannel();
		channel.setId("channel-1");
		channel.setAdapterCode("STANDARD");
		channel.setStatus("ACTIVE");
		AdxMediaCredential credential = new AdxMediaCredential();
		credential.setId("credential-1");
		credential.setMediaAccountId("media-1");
		credential.setStatus("ACTIVE");
		when(accountMapper.selectById("media-1")).thenReturn(account);
		when(accountMapper.selectOne(any())).thenReturn(account);
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		when(credentialMapper.selectCount(any())).thenReturn(1L);
		when(credentialMapper.selectById("credential-1")).thenReturn(credential);
		when(credentialMapper.selectList(any())).thenReturn(java.util.List.of(credential));
		when(adapterService.requireEnabled("STANDARD", "DOWNSTREAM")).thenReturn(new AdxAdapterService.PublishedAdapter(
				"STANDARD", "DOWNSTREAM", "POST", "POST", java.util.Set.of(), java.util.Set.of(), java.util.List.of()));

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			MediaAccountViews.Preflight result = service.preflight("media-1", new MediaAccountRequests.Preflight(3L));

			assertThat(result.mode()).isEqualTo("CONFIG_ONLY");
			assertThat(result.networkCalled()).isFalse();
			assertThat(result.status()).isEqualTo("READY");
		}
		verify(crypto, never()).decrypt(any());
	}

	private AdxMediaAccount account() {
		AdxMediaAccount account = new AdxMediaAccount();
		account.setId("media-1");
		account.setChannelId("channel-1");
		account.setCode("MA_TEST");
		account.setName("测试媒体账号");
		account.setStatus("DISABLED");
		account.setConfigVersion(3L);
		account.setCredentialRevision(0L);
		return account;
	}

}
