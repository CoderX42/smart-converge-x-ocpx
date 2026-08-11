package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.TaskRequests;
import com.yzytech.scx.ocpx.admin.dto.TaskViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaCredential;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.entity.OcpxTask;
import com.yzytech.scx.ocpx.admin.entity.OcpxTaskChangeAudit;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaCredentialMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxSystemUserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxTaskChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxTaskMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.entity.SysUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxTaskServiceTest {

	@BeforeAll
	static void initializeTableMetadata() {
		for (Class<?> entity : new Class<?>[] { OcpxTask.class, OcpxMonitorLink.class }) {
			TableInfoHelper
				.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), entity.getSimpleName()), entity);
		}
	}

	private final OcpxTaskMapper taskMapper = mock(OcpxTaskMapper.class);

	private final OcpxTaskChangeAuditMapper auditMapper = mock(OcpxTaskChangeAuditMapper.class);

	private final OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);

	private final OcpxProductMapper productMapper = mock(OcpxProductMapper.class);

	private final OcpxAppLinkMapper appLinkMapper = mock(OcpxAppLinkMapper.class);

	private final OcpxChannelMapper channelMapper = mock(OcpxChannelMapper.class);

	private final OcpxMediaAccountMapper accountMapper = mock(OcpxMediaAccountMapper.class);

	private final OcpxMediaCredentialMapper credentialMapper = mock(OcpxMediaCredentialMapper.class);

	private final OcpxSystemUserMapper userMapper = mock(OcpxSystemUserMapper.class);

	private final OcpxMonitorLinkMapper monitorMapper = mock(OcpxMonitorLinkMapper.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxConfigChangeService configChangeService = mock(OcpxConfigChangeService.class);

	private final OcpxTaskEventBindingService taskEventBindingService = mock(OcpxTaskEventBindingService.class);

	private OcpxTaskService service;

	@BeforeEach
	void setUp() {
		service = new OcpxTaskService(taskMapper, auditMapper, advertiserMapper, productMapper, appLinkMapper,
				channelMapper, accountMapper, credentialMapper, userMapper, monitorMapper, accessScope,
				configChangeService, taskEventBindingService);
		when(taskMapper.selectCount(any())).thenReturn(0L);
		when(monitorMapper.selectCount(any())).thenReturn(0L);
		when(productMapper.selectById("product-1")).thenReturn(product());
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser());
		when(appLinkMapper.selectById("click-1")).thenReturn(link("click-1"));
		when(appLinkMapper.selectById("exposure-1")).thenReturn(link("exposure-1"));
		when(accountMapper.selectById("account-1")).thenReturn(account());
		when(channelMapper.selectById("channel-1")).thenReturn(channel());
		when(credentialMapper.selectById("credential-1")).thenReturn(credential());
		when(userMapper.selectById("user-1")).thenReturn(operator());
		when(taskMapper.insert(any(OcpxTask.class))).thenAnswer(invocation -> {
			((OcpxTask) invocation.getArgument(0)).setId("task-1");
			return 1;
		});
	}

	@Test
	void createStaysDraftFreezesCredentialRevisionAndDoesNotPublish() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			TaskViews.Detail detail = service.create("task_request_key_0001",
					new TaskRequests.Create("product-1", "click-1", "exposure-1", "account-1", "credential-1", "user-1",
							"暑期拉新", true, true, 86400, "OFF", "draft only"));

			assertThat(detail.status()).isEqualTo("DRAFT");
			assertThat(detail.mediaCredentialSecretRevision()).isEqualTo(7);
			assertThat(detail.publishedTaskVersion()).isZero();
			assertThat(detail.republishRequired()).isTrue();
		}
		verify(configChangeService, never()).enqueueMonitor(any(), any(Long.class), any());
		verify(configChangeService, never()).taskChanged(any(), any());
		ArgumentCaptor<OcpxTaskChangeAudit> audit = ArgumentCaptor.forClass(OcpxTaskChangeAudit.class);
		verify(auditMapper).insert(audit.capture());
		assertThat(audit.getValue().getChangeSummary()).contains("未发布", "未创建 Outbox", "未修改旧快照");
	}

	@Test
	void rejectsRtaAndInvalidExposureCombinations() {
		assertThatThrownBy(() -> service.create("task_request_key_0002",
				new TaskRequests.Create("product-1", "click-1", null, "account-1", "credential-1", "user-1", "bad-rta",
						false, false, 1, "ON", null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("RTA");
		assertThatThrownBy(() -> service.create("task_request_key_0003",
				new TaskRequests.Create("product-1", "click-1", null, "account-1", "credential-1", "user-1",
						"bad-exposure", true, true, 1, "OFF", null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("曝光");
	}

	@Test
	void rejectsStaleVersionAndFakeTrafficStatus() {
		OcpxTask task = task();
		when(taskMapper.selectOne(any())).thenReturn(task);
		assertThatThrownBy(() -> service.update("task-1",
				new TaskRequests.Update("product-1", "click-1", null, "account-1", "credential-1", "user-1", "暑期拉新",
						false, false, 0, "OFF", null, 2L)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("VERSION_CONFLICT");
		assertThatThrownBy(() -> service.changeStatus("task-1", new TaskRequests.StatusAction(3L, "PAUSED", "stop")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("REQUIRES_S7");
	}

	@Test
	void enforcesAdvertiserChannelAndMediaAccountScopeTogether() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			service.create("task_request_key_0004", new TaskRequests.Create("product-1", "click-1", null, "account-1",
					"credential-1", "user-1", "范围任务", false, false, 0, "OFF", null));
		}
		verify(accessScope).require(OcpxAccessScope.ScopeType.ADVERTISER, "advertiser-1",
				OcpxAccessScope.PermissionLevel.OPERATE);
		verify(accessScope).require(OcpxAccessScope.ScopeType.CHANNEL, "channel-1",
				OcpxAccessScope.PermissionLevel.OPERATE);
		verify(accessScope).require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, "account-1",
				OcpxAccessScope.PermissionLevel.OPERATE);
	}

	@Test
	void listAndOptionsDoNotLeakWhenAnyScopeDimensionIsEmpty() {
		when(accessScope.current()).thenReturn(new OcpxAccessScope.Snapshot(1L, null,
				Map.of(OcpxAccessScope.ScopeType.ADVERTISER, Map.of("advertiser-1", OcpxAccessScope.PermissionLevel.VIEW),
						OcpxAccessScope.ScopeType.CHANNEL, Map.of("channel-1", OcpxAccessScope.PermissionLevel.VIEW))));

		IPage<TaskViews.ListItem> page = service.list(new Page<>(1, 20), null, null, null, null, null, null, null, null,
				null);
		TaskViews.Options options = service.options(null);

		assertThat(page.getTotal()).isZero();
		assertThat(page.getRecords()).isEmpty();
		assertThat(options.advertisers()).isEmpty();
		assertThat(options.mediaAccounts()).isEmpty();
	}

	@Test
	void detailGuessRequiresAllThreeObjectScopes() {
		when(taskMapper.selectById("task-1")).thenReturn(task());

		service.detail("task-1");

		verify(accessScope).require(OcpxAccessScope.ScopeType.ADVERTISER, "advertiser-1",
				OcpxAccessScope.PermissionLevel.VIEW);
		verify(accessScope).require(OcpxAccessScope.ScopeType.CHANNEL, "channel-1", OcpxAccessScope.PermissionLevel.VIEW);
		verify(accessScope).require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, "account-1",
				OcpxAccessScope.PermissionLevel.VIEW);
	}

	@Test
	void rejectsTamperedChildRelationshipIds() {
		OcpxAppLink mismatched = link("click-1");
		mismatched.setProductId("other-product");
		when(appLinkMapper.selectById("click-1")).thenReturn(mismatched);

		assertThatThrownBy(() -> service.create("task_request_key_0005",
				new TaskRequests.Create("product-1", "click-1", null, "account-1", "credential-1", "user-1", "篡改关联",
						false, false, 0, "OFF", null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不一致");
	}

	@Test
	void archiveProtectsAnyMonitorReference() {
		when(taskMapper.selectOne(any())).thenReturn(task());
		when(monitorMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.archive("task-1", new TaskRequests.VersionedAction(3L, "仍有引用")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("REFERENCE_PROTECTED");
	}

	@Test
	void responseContractsExposeRevisionButNotCredentialMaterialOrUrls() {
		assertThat(Arrays.stream(TaskViews.Detail.class.getRecordComponents()).map(component -> component.getName()))
			.contains("mediaCredentialSecretRevision")
			.doesNotContain("credentialCiphertext", "credentialSecret", "redirectUrl", "downloadUrl",
					"createRequestKey", "createRequestHash");
	}

	private OcpxAdvertiser advertiser() {
		OcpxAdvertiser value = new OcpxAdvertiser();
		value.setId("advertiser-1");
		value.setAdvertiserName("广告主");
		value.setStatus("ACTIVE");
		return value;
	}

	private OcpxProduct product() {
		OcpxProduct value = new OcpxProduct();
		value.setId("product-1");
		value.setAdvertiserId("advertiser-1");
		value.setName("产品");
		value.setStatus("ACTIVE");
		return value;
	}

	private OcpxAppLink link(String id) {
		OcpxAppLink value = new OcpxAppLink();
		value.setId(id);
		value.setAdvertiserId("advertiser-1");
		value.setProductId("product-1");
		value.setLinkName(id);
		value.setLinkType("ANDROID");
		value.setMappingStatus("READY");
		value.setStatus("ACTIVE");
		return value;
	}

	private OcpxChannel channel() {
		OcpxChannel value = new OcpxChannel();
		value.setId("channel-1");
		value.setChannelName("渠道");
		value.setStatus("ACTIVE");
		return value;
	}

	private OcpxMediaAccount account() {
		OcpxMediaAccount value = new OcpxMediaAccount();
		value.setId("account-1");
		value.setChannelId("channel-1");
		value.setName("媒体账号");
		value.setStatus("ACTIVE");
		return value;
	}

	private OcpxMediaCredential credential() {
		OcpxMediaCredential value = new OcpxMediaCredential();
		value.setId("credential-1");
		value.setMediaAccountId("account-1");
		value.setCurrentSecretRevision(7L);
		value.setStatus("ACTIVE");
		return value;
	}

	private SysUser operator() {
		SysUser value = new SysUser();
		value.setId("user-1");
		value.setUsername("operator");
		value.setNickname("负责人");
		value.setStatus("0");
		value.setDelFlag("0");
		return value;
	}

	private OcpxTask task() {
		OcpxTask value = new OcpxTask();
		value.setId("task-1");
		value.setAdvertiserId("advertiser-1");
		value.setProductId("product-1");
		value.setClickAppLinkId("click-1");
		value.setChannelId("channel-1");
		value.setMediaAccountId("account-1");
		value.setMediaCredentialId("credential-1");
		value.setOperatorUserId("user-1");
		value.setTaskName("暑期拉新");
		value.setExposureEnabled(false);
		value.setExposureAsClick(false);
		value.setDedupWindowSeconds(0);
		value.setRtaMode("OFF");
		value.setStatus("DRAFT");
		value.setConfigVersion(3L);
		value.setPublishedTaskVersion(0L);
		return value;
	}

}
