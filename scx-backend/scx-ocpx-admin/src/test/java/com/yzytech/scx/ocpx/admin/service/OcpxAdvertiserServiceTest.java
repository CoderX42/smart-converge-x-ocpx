package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiserCredentialRevision;
import com.yzytech.scx.ocpx.admin.entity.OcpxIntegrationDocument;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserCredentialRevisionMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxIntegrationDocumentMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserRoleMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxAdvertiserServiceTest {

	private final OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);

	private final OcpxAdvertiserCredentialRevisionMapper credentialMapper = mock(
			OcpxAdvertiserCredentialRevisionMapper.class);

	private final OcpxIntegrationDocumentMapper documentMapper = mock(OcpxIntegrationDocumentMapper.class);

	private final OcpxAdvertiserChangeAuditMapper auditMapper = mock(OcpxAdvertiserChangeAuditMapper.class);

	private final OcpxMonitorLinkMapper monitorMapper = mock(OcpxMonitorLinkMapper.class);

	private final OcpxSystemUserMapper systemUserMapper = mock(OcpxSystemUserMapper.class);

	private final OcpxSystemUserRoleMapper systemUserRoleMapper = mock(OcpxSystemUserRoleMapper.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxUserScopeService userScopeService = mock(OcpxUserScopeService.class);

	private final OcpxConfigChangeService configChangeService = mock(OcpxConfigChangeService.class);

	private final OcpxAdapterService adapterService = mock(OcpxAdapterService.class);

	private final ProtocolCrypto protocolCrypto = mock(ProtocolCrypto.class);

	private final StorageUploadService storageUploadService = mock(StorageUploadService.class);

	private OcpxAdvertiserService service;

	@BeforeEach
	void setUp() {
		service = new OcpxAdvertiserService(advertiserMapper, credentialMapper, documentMapper, auditMapper, monitorMapper,
				systemUserMapper, systemUserRoleMapper, accessScope, userScopeService, configChangeService, adapterService,
				protocolCrypto, storageUploadService);
		when(adapterService.requireAdvertiserCapability("COCO")).thenReturn(new AdvertiserViews.AdapterCapability("COCO",
				"Coco", 3L, true, true, List.of("NONE"), List.of("CLICK"), 3000));
		when(monitorMapper.selectCount(any())).thenReturn(0L);
	}

	@Test
	void listAllowsMissingOptionalAdapterFilter() {
		when(accessScope.current()).thenReturn(
				new OcpxAccessScope.Snapshot(1L, OcpxAccessScope.PermissionLevel.MANAGE, Map.of()));
		Page<OcpxAdvertiser> selected = new Page<>(1, 500);
		selected.setRecords(List.of());
		when(advertiserMapper.selectPage(any(), any())).thenReturn(selected);

		var result = service.list(new Page<>(1, 500), null, null, null, null);

		assertThat(result.getRecords()).isEmpty();
		verify(advertiserMapper).selectPage(any(), any());
	}

	@Test
	void createEncryptsCredentialAndNeverWritesLegacyPlaintext() {
		when(advertiserMapper.selectCount(any())).thenReturn(0L);
		when(systemUserMapper.selectCount(any())).thenReturn(0L);
		when(systemUserRoleMapper.selectCount(any())).thenReturn(0L);
		when(protocolCrypto.encrypt("initial-credential-value")).thenReturn("encrypted-value");
		when(advertiserMapper.insert(any(OcpxAdvertiser.class))).thenAnswer(invocation -> {
			((OcpxAdvertiser) invocation.getArgument(0)).setId("advertiser-1");
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

		ArgumentCaptor<OcpxAdvertiser> advertiser = ArgumentCaptor.forClass(OcpxAdvertiser.class);
		verify(advertiserMapper).insert(advertiser.capture());
		assertThat(advertiser.getValue().getSecretKey()).isNull();
		assertThat(advertiser.getValue().getStatus()).isEqualTo("DISABLED");
		ArgumentCaptor<OcpxAdvertiserCredentialRevision> credential = ArgumentCaptor
			.forClass(OcpxAdvertiserCredentialRevision.class);
		verify(credentialMapper).insert(credential.capture());
		assertThat(credential.getValue().getSecretCiphertext()).isEqualTo("encrypted-value");
		assertThat(credential.getValue().getStorageMode()).isEqualTo("ENCRYPTED");
		verify(userScopeService).ensureAdvertiserScope("user-1", "advertiser-1");
		assertThat(Arrays.stream(AdvertiserViews.Detail.class.getRecordComponents()).map(component -> component.getName()))
			.doesNotContain("secretKey", "credentialInput", "credentialRef", "secretCiphertext");
	}

	@Test
	void firstAuthorizedRotationEncryptsLegacyRevisionBeforeClearingOldColumn() {
		OcpxAdvertiser advertiser = advertiser(1L, 1L);
		advertiser.setSecretKey("legacy-credential-value");
		advertiser.setCredentialRef("legacy-ref");
		OcpxAdvertiserCredentialRevision previous = new OcpxAdvertiserCredentialRevision();
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
		ArgumentCaptor<OcpxAdvertiserCredentialRevision> newCredential = ArgumentCaptor
			.forClass(OcpxAdvertiserCredentialRevision.class);
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
			.insert(any(OcpxIntegrationDocument.class));
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
		OcpxAdvertiser advertiser = advertiser(2L, 0L);
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		when(monitorMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.retireDocument("advertiser-1", "document-1",
				new AdvertiserRequests.RetireDocument("obsolete"))).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("已发布路由引用");
	}

	private OcpxAdvertiser advertiser(long configVersion, long credentialRevision) {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
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
