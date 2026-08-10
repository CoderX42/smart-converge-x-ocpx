package com.yzytech.scx.adx.service;

import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.adx.dto.AdvertiserRequests;
import com.yzytech.scx.adx.dto.AdvertiserViews;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxAdvertiserCredentialRevision;
import com.yzytech.scx.adx.entity.AdxIntegrationDocument;
import com.yzytech.scx.adx.mapper.AdxAdvertiserChangeAuditMapper;
import com.yzytech.scx.adx.mapper.AdxAdvertiserCredentialRevisionMapper;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxIntegrationDocumentMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import com.yzytech.scx.adx.mapper.AdxSystemUserMapper;
import com.yzytech.scx.adx.mapper.AdxSystemUserRoleMapper;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.service.StorageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdxAdvertiserServiceTest {

	private final AdxAdvertiserMapper advertiserMapper = mock(AdxAdvertiserMapper.class);

	private final AdxAdvertiserCredentialRevisionMapper credentialMapper = mock(
			AdxAdvertiserCredentialRevisionMapper.class);

	private final AdxIntegrationDocumentMapper documentMapper = mock(AdxIntegrationDocumentMapper.class);

	private final AdxAdvertiserChangeAuditMapper auditMapper = mock(AdxAdvertiserChangeAuditMapper.class);

	private final AdxMonitorLinkMapper monitorMapper = mock(AdxMonitorLinkMapper.class);

	private final AdxSystemUserMapper systemUserMapper = mock(AdxSystemUserMapper.class);

	private final AdxSystemUserRoleMapper systemUserRoleMapper = mock(AdxSystemUserRoleMapper.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxUserScopeService userScopeService = mock(AdxUserScopeService.class);

	private final AdxConfigChangeService configChangeService = mock(AdxConfigChangeService.class);

	private final AdxAdapterService adapterService = mock(AdxAdapterService.class);

	private final ProtocolCrypto protocolCrypto = mock(ProtocolCrypto.class);

	private final StorageUploadService storageUploadService = mock(StorageUploadService.class);

	private AdxAdvertiserService service;

	@BeforeEach
	void setUp() {
		service = new AdxAdvertiserService(advertiserMapper, credentialMapper, documentMapper, auditMapper, monitorMapper,
				systemUserMapper, systemUserRoleMapper, accessScope, userScopeService, configChangeService, adapterService,
				protocolCrypto, storageUploadService);
		when(adapterService.requireAdvertiserCapability("COCO")).thenReturn(new AdvertiserViews.AdapterCapability("COCO",
				"Coco", 3L, true, true, List.of("NONE"), List.of("CLICK"), 3000));
		when(monitorMapper.selectCount(any())).thenReturn(0L);
	}

	@Test
	void createEncryptsCredentialAndNeverWritesLegacyPlaintext() {
		when(advertiserMapper.selectCount(any())).thenReturn(0L);
		when(systemUserMapper.selectCount(any())).thenReturn(0L);
		when(systemUserRoleMapper.selectCount(any())).thenReturn(0L);
		when(protocolCrypto.encrypt("initial-credential-value")).thenReturn("encrypted-value");
		when(advertiserMapper.insert(any(AdxAdvertiser.class))).thenAnswer(invocation -> {
			((AdxAdvertiser) invocation.getArgument(0)).setId("advertiser-1");
			return 1;
		});
		when(systemUserMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
			((SysUser) invocation.getArgument(0)).setId("user-1");
			return 1;
		});
		AdvertiserRequests.Create request = new AdvertiserRequests.Create("测试广告主", "adv_test", "COCO",
				"https://api.example.test/callback", null, "api.example.test", "adv-account", "password-123",
				"initial-credential-value");

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			AdvertiserViews.Detail result = service.create("create-request-0001", request);

			assertThat(result.credentialConfigured()).isTrue();
			assertThat(result.credentialRevision()).isEqualTo(1);
		}

		ArgumentCaptor<AdxAdvertiser> advertiser = ArgumentCaptor.forClass(AdxAdvertiser.class);
		verify(advertiserMapper).insert(advertiser.capture());
		assertThat(advertiser.getValue().getSecretKey()).isNull();
		assertThat(advertiser.getValue().getStatus()).isEqualTo("DISABLED");
		ArgumentCaptor<AdxAdvertiserCredentialRevision> credential = ArgumentCaptor
			.forClass(AdxAdvertiserCredentialRevision.class);
		verify(credentialMapper).insert(credential.capture());
		assertThat(credential.getValue().getSecretCiphertext()).isEqualTo("encrypted-value");
		assertThat(credential.getValue().getStorageMode()).isEqualTo("ENCRYPTED");
		verify(userScopeService).ensureAdvertiserScope("user-1", "advertiser-1");
		assertThat(Arrays.stream(AdvertiserViews.Detail.class.getRecordComponents()).map(component -> component.getName()))
			.doesNotContain("secretKey", "credentialInput", "credentialRef", "secretCiphertext");
	}

	@Test
	void firstAuthorizedRotationEncryptsLegacyRevisionBeforeClearingOldColumn() {
		AdxAdvertiser advertiser = advertiser(1L, 1L);
		advertiser.setSecretKey("legacy-credential-value");
		advertiser.setCredentialRef("legacy-ref");
		AdxAdvertiserCredentialRevision previous = new AdxAdvertiserCredentialRevision();
		previous.setId("credential-1");
		previous.setAdvertiserId(advertiser.getId());
		previous.setCredentialRevision(1L);
		previous.setStorageMode("LEGACY");
		previous.setStatus("ACTIVE");
		when(advertiserMapper.selectOne(any())).thenReturn(advertiser);
		when(credentialMapper.selectOne(any())).thenReturn(previous);
		when(protocolCrypto.encrypt("legacy-credential-value")).thenReturn("encrypted-legacy");
		when(protocolCrypto.encrypt("replacement-credential-value")).thenReturn("encrypted-replacement");
		when(configChangeService.advertiserChanged(any(), any())).thenReturn(2);

		try (MockedStatic<StpUtil> safe = mockStatic(StpUtil.class);
				MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			AdvertiserViews.CredentialState result = service.rotateCredential("advertiser-1",
					new AdvertiserRequests.RotateCredential("replacement-credential-value", 1L,
							"scheduled rotation; secret=accidental-reason-value"));

			assertThat(result.configured()).isTrue();
			assertThat(result.revision()).isEqualTo(2);
			assertThat(result.republishRequiredCount()).isEqualTo(2);
			safe.verify(StpUtil::checkSafe);
		}

		assertThat(previous.getStorageMode()).isEqualTo("ENCRYPTED");
		assertThat(previous.getSecretCiphertext()).isEqualTo("encrypted-legacy");
		assertThat(previous.getStatus()).isEqualTo("OVERLAP");
		assertThat(advertiser.getSecretKey()).isNull();
		assertThat(advertiser.getCredentialRevision()).isEqualTo(2);
		ArgumentCaptor<AdxAdvertiserCredentialRevision> newCredential = ArgumentCaptor
			.forClass(AdxAdvertiserCredentialRevision.class);
		verify(credentialMapper).insert(newCredential.capture());
		assertThat(newCredential.getValue().getReason()).contains("secret=[REDACTED]")
			.doesNotContain("accidental-reason-value", "replacement-credential-value");
	}

	@Test
	void updateRejectsStaleConfigVersion() {
		when(advertiserMapper.selectOne(any())).thenReturn(advertiser(5L, 0L));

		assertThatThrownBy(() -> service.update("advertiser-1", new AdvertiserRequests.Update("测试广告主", "COCO",
				"https://api.example.test/callback", null, "api.example.test", 4L)))
			.isInstanceOf(com.yzytech.scx.common.security.handler.BootBusinessException.class)
			.hasMessageContaining("刷新");
	}

	@Test
	void uploadDeletesManagedObjectWhenMetadataPersistenceFails() {
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser(1L, 0L));
		ManagedStorageObject stored = new ManagedStorageObject("storage-1", "MINIO", "managed/object.pdf", null);
		when(storageUploadService.uploadManaged(any(), any(), any(), anyLong())).thenReturn(stored);
		doThrow(new IllegalStateException("database unavailable")).when(documentMapper)
			.insert(any(AdxIntegrationDocument.class));
		MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf",
				"safe-document".getBytes());

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			assertThatThrownBy(
					() -> service.uploadDocument("advertiser-1", "PROTOCOL", "v1", file))
				.isInstanceOf(IllegalStateException.class);
		}

		verify(storageUploadService).delete(stored);
	}

	@Test
	void referencedDocumentRevisionCannotBeRetired() {
		AdxAdvertiser advertiser = advertiser(2L, 0L);
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		when(monitorMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.retireDocument("advertiser-1", "document-1",
				new AdvertiserRequests.RetireDocument("obsolete"))).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("已发布路由引用");
	}

	private AdxAdvertiser advertiser(long configVersion, long credentialRevision) {
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setAdvertiserCode("adv_test");
		advertiser.setAdvertiserName("测试广告主");
		advertiser.setAdapterCode("COCO");
		advertiser.setAdapterVersion(3L);
		advertiser.setBidUrl("https://api.example.test/callback");
		advertiser.setUpstreamHostAllowlist("api.example.test");
		advertiser.setAccount("adv-account");
		advertiser.setStatus("DISABLED");
		advertiser.setConfigVersion(configVersion);
		advertiser.setCredentialRevision(credentialRevision);
		advertiser.setDocumentRevision(0L);
		return advertiser;
	}

}
