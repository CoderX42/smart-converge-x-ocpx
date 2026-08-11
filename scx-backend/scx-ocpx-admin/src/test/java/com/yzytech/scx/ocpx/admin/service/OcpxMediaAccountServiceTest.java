package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yzytech.scx.ocpx.admin.dto.MediaAccountRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaAccountViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccountChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccountOwner;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaCredential;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaCredentialRevision;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxIntegrationDocumentMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountOwnerMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaCredentialMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaCredentialRevisionMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
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

class OcpxMediaAccountServiceTest {

	@BeforeAll
	static void initializeTableMetadata() {
		for (Class<?> entity : new Class<?>[] { OcpxMediaAccount.class, OcpxMediaCredential.class,
				OcpxMediaCredentialRevision.class, OcpxMediaAccountOwner.class, OcpxMonitorLink.class }) {
			TableInfoHelper
				.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), entity.getSimpleName()), entity);
		}
	}

	private final OcpxMediaAccountMapper accountMapper = mock(OcpxMediaAccountMapper.class);

	private final OcpxMediaCredentialMapper credentialMapper = mock(OcpxMediaCredentialMapper.class);

	private final OcpxMediaCredentialRevisionMapper revisionMapper = mock(OcpxMediaCredentialRevisionMapper.class);

	private final OcpxMediaAccountOwnerMapper ownerMapper = mock(OcpxMediaAccountOwnerMapper.class);

	private final OcpxMediaAccountChangeAuditMapper auditMapper = mock(OcpxMediaAccountChangeAuditMapper.class);

	private final OcpxChannelMapper channelMapper = mock(OcpxChannelMapper.class);

	private final OcpxIntegrationDocumentMapper documentMapper = mock(OcpxIntegrationDocumentMapper.class);

	private final OcpxMonitorLinkMapper monitorMapper = mock(OcpxMonitorLinkMapper.class);

	private final OcpxSystemUserMapper userMapper = mock(OcpxSystemUserMapper.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxUserScopeService userScopeService = mock(OcpxUserScopeService.class);

	private final OcpxConfigChangeService configChangeService = mock(OcpxConfigChangeService.class);

	private final OcpxAdapterService adapterService = mock(OcpxAdapterService.class);

	private final ProtocolCrypto crypto = mock(ProtocolCrypto.class);

	private OcpxMediaAccountService service;

	@BeforeEach
	void setUp() {
		service = new OcpxMediaAccountService(accountMapper, credentialMapper, revisionMapper, ownerMapper, auditMapper,
				channelMapper, documentMapper, monitorMapper, userMapper, accessScope, userScopeService,
				configChangeService, adapterService, crypto);
		when(ownerMapper.selectCount(any())).thenReturn(0L);
		when(monitorMapper.selectCount(any())).thenReturn(0L);
	}

	@Test
	void createsWriteOnlyEncryptedCredentialWithoutResponseOrAuditLeakage() {
		OcpxMediaAccount account = account();
		when(accountMapper.selectById("media-1")).thenReturn(account);
		when(accountMapper.selectOne(any())).thenReturn(account);
		when(credentialMapper.insert(any(OcpxMediaCredential.class))).thenAnswer(invocation -> {
			((OcpxMediaCredential) invocation.getArgument(0)).setId("credential-1");
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

		ArgumentCaptor<OcpxMediaCredential> stored = ArgumentCaptor.forClass(OcpxMediaCredential.class);
		verify(credentialMapper).insert(stored.capture());
		assertThat(stored.getValue().getLoginIdentifierCiphertext()).isEqualTo("encrypted-identifier");
		assertThat(stored.getValue().getLoginIdentifierHash()).isEqualTo("blind-index");
		assertThat(stored.getValue().getStatus()).isEqualTo("PENDING");
		ArgumentCaptor<OcpxMediaCredentialRevision> revision = ArgumentCaptor.forClass(OcpxMediaCredentialRevision.class);
		verify(revisionMapper).insert(revision.capture());
		assertThat(revision.getValue().getSecretCiphertext()).isEqualTo("encrypted-secret");
		assertThat(Arrays.stream(MediaAccountViews.Credential.class.getRecordComponents())
			.map(component -> component.getName())).doesNotContain("loginIdentifier", "loginIdentifierCiphertext",
					"secretInput", "secretCiphertext", "credentialRef");
		ArgumentCaptor<OcpxMediaAccountChangeAudit> audit = ArgumentCaptor.forClass(OcpxMediaAccountChangeAudit.class);
		verify(auditMapper).insert(audit.capture());
		assertThat(audit.getValue().getChangeSummary()).doesNotContain("owner@example", "local-secret-value");
	}

	@Test
	void configurationPreflightNeverDecryptsOrCallsMediaNetwork() {
		OcpxMediaAccount account = account();
		account.setStatus("DISABLED");
		account.setDefaultCredentialId("credential-1");
		OcpxChannel channel = new OcpxChannel();
		channel.setId("channel-1");
		channel.setAdapterCode("STANDARD");
		channel.setStatus("ACTIVE");
		OcpxMediaCredential credential = new OcpxMediaCredential();
		credential.setId("credential-1");
		credential.setMediaAccountId("media-1");
		credential.setStatus("ACTIVE");
		when(accountMapper.selectById("media-1")).thenReturn(account);
		when(accountMapper.selectOne(any())).thenReturn(account);
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		when(credentialMapper.selectCount(any())).thenReturn(1L);
		when(credentialMapper.selectById("credential-1")).thenReturn(credential);
		when(credentialMapper.selectList(any())).thenReturn(java.util.List.of(credential));
		when(adapterService.requireEnabled("STANDARD", "DOWNSTREAM")).thenReturn(new OcpxAdapterService.PublishedAdapter(
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

	private OcpxMediaAccount account() {
		OcpxMediaAccount account = new OcpxMediaAccount();
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
