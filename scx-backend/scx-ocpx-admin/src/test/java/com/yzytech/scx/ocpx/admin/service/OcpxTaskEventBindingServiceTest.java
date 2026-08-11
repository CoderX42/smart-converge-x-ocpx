package com.yzytech.scx.ocpx.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.ocpx.admin.dto.EventRequests;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannelEventMapping;
import com.yzytech.scx.ocpx.admin.entity.OcpxEventDefinition;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.entity.OcpxTask;
import com.yzytech.scx.ocpx.admin.entity.OcpxTaskEventBinding;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelEventMappingMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventDefinitionMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductEventPolicyMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxTaskEventBindingMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxTaskMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class OcpxTaskEventBindingServiceTest {

	@Mock
	private OcpxTaskMapper taskMapper;

	@Mock
	private OcpxTaskEventBindingMapper bindingMapper;

	@Mock
	private OcpxEventDefinitionMapper definitionMapper;

	@Mock
	private OcpxChannelEventMappingMapper mappingMapper;

	@Mock
	private OcpxProductEventPolicyMapper productPolicyMapper;

	@Mock
	private OcpxProductMapper productMapper;

	@Mock
	private OcpxMediaAccountMapper mediaAccountMapper;

	@Mock
	private OcpxChannelMapper channelMapper;

	@Mock
	private OcpxEventChangeAuditMapper auditMapper;

	@Mock
	private OcpxAccessScope accessScope;

	@Mock
	private OcpxConfigChangeService configChangeService;

	private OcpxTaskEventBindingService service;

	@BeforeEach
	void setUp() {
		service = new OcpxTaskEventBindingService(taskMapper, bindingMapper, definitionMapper, mappingMapper,
				productPolicyMapper, productMapper, mediaAccountMapper, channelMapper, auditMapper, accessScope,
				configChangeService);
	}

	@Test
	void createOptionsRejectsIndependentlyScopedButCrossChannelParentIds() {
		OcpxProduct product = new OcpxProduct();
		product.setId("product-1");
		product.setAdvertiserId("advertiser-1");
		product.setStatus("ACTIVE");
		OcpxMediaAccount account = new OcpxMediaAccount();
		account.setId("media-1");
		account.setChannelId("other-channel");
		account.setStatus("ACTIVE");
		when(productMapper.selectById("product-1")).thenReturn(product);
		when(mediaAccountMapper.selectById("media-1")).thenReturn(account);

		assertThatThrownBy(() -> service.options("channel-1", "product-1", "media-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("关系不一致");
		verify(mappingMapper, never()).selectList(any());
	}

	@Test
	void staleTaskVersionConflictsAfterAllThreeObjectScopesAreChecked() {
		OcpxTask task = task();
		task.setConfigVersion(3L);
		when(taskMapper.selectOne(any())).thenReturn(task);

		assertThatThrownBy(() -> service.replace("task-1", new EventRequests.ReplaceTaskBindings(2L, List.of())))
			.isInstanceOfSatisfying(BootBusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
		verify(accessScope).require(OcpxAccessScope.ScopeType.ADVERTISER, "advertiser-1",
				OcpxAccessScope.PermissionLevel.OPERATE);
		verify(accessScope).require(OcpxAccessScope.ScopeType.CHANNEL, "channel-1",
				OcpxAccessScope.PermissionLevel.OPERATE);
		verify(accessScope).require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, "media-1",
				OcpxAccessScope.PermissionLevel.OPERATE);
		verify(bindingMapper, never()).insert(any(OcpxTaskEventBinding.class));
	}

	@Test
	void rejectsCrossChannelMappingAndDoesNotWriteAnyBinding() {
		OcpxTask task = task();
		OcpxEventDefinition definition = new OcpxEventDefinition();
		definition.setId("definition-1");
		definition.setMappingState("CANONICAL");
		definition.setStatus("ACTIVE");
		OcpxChannelEventMapping mapping = new OcpxChannelEventMapping();
		mapping.setId("mapping-1");
		mapping.setChannelId("other-channel");
		mapping.setEventDefinitionId("definition-1");
		mapping.setDirection("DOWNSTREAM_DELIVERY");
		mapping.setMappingState("CANONICAL");
		mapping.setStatus("ACTIVE");
		when(definitionMapper.selectById("definition-1")).thenReturn(definition);
		when(mappingMapper.selectById("mapping-1")).thenReturn(mapping);

		var input = new EventRequests.TaskBindingInput("definition-1", "mapping-1", 10000, 0L, false);
		assertThatThrownBy(() -> service.syncWithinTaskSave(task, List.of(input), false, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("当前渠道");
		verify(bindingMapper, never()).insert(any(OcpxTaskEventBinding.class));
	}

	@Test
	void rejectsRatioAndLimitOutsideTheServiceContractBeforeLookingUpObjects() {
		var ratio = new EventRequests.TaskBindingInput("definition-1", "mapping-1", 10001, 0L, false);
		var limit = new EventRequests.TaskBindingInput("definition-1", "mapping-1", 10000, -1L, false);

		assertThatThrownBy(() -> service.syncWithinTaskSave(task(), List.of(ratio), false, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("10000 basis points");
		assertThatThrownBy(() -> service.syncWithinTaskSave(task(), List.of(limit), false, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不能为负数");
		verify(definitionMapper, never()).selectById(any());
	}

	@Test
	void snapshotPreviewIsPureAndFreezesExactRevisions() {
		OcpxTask task = task();
		task.setConfigVersion(7L);
		when(taskMapper.selectById("task-1")).thenReturn(task);
		when(bindingMapper.selectList(any())).thenReturn(List.of());

		var preview = service.previewSnapshot("task-1");

		assertThat(preview.schemaVersion()).isEqualTo(2);
		assertThat(preview.immutable()).isTrue();
		assertThat(preview.persisted()).isFalse();
		assertThat(preview.outboxCreated()).isFalse();
		verify(bindingMapper, never()).insert(any(OcpxTaskEventBinding.class));
		verify(taskMapper, never()).updateById(any(OcpxTask.class));
	}

	@Test
	void snapshotPreviewRejectsAReferencedDefinitionThatWasDisabledAfterDraftSave() {
		OcpxTaskEventBinding binding = new OcpxTaskEventBinding();
		binding.setId("binding-1");
		binding.setTaskId("task-1");
		binding.setEventDefinitionId("definition-1");
		binding.setChannelEventMappingId("mapping-1");
		binding.setCallbackRatioBasisPoints(10000);
		binding.setOverLimitDelivery(false);
		binding.setStatus("ACTIVE");
		OcpxEventDefinition definition = new OcpxEventDefinition();
		definition.setId("definition-1");
		definition.setMappingState("CANONICAL");
		definition.setStatus("DISABLED");
		OcpxChannelEventMapping mapping = new OcpxChannelEventMapping();
		mapping.setId("mapping-1");
		mapping.setMappingState("CANONICAL");
		mapping.setStatus("ACTIVE");
		when(taskMapper.selectById("task-1")).thenReturn(task());
		when(bindingMapper.selectList(any())).thenReturn(List.of(binding));
		when(definitionMapper.selectById("definition-1")).thenReturn(definition);
		when(mappingMapper.selectById("mapping-1")).thenReturn(mapping);

		var preview = service.previewSnapshot("task-1");

		assertThat(preview.items()).isEmpty();
		assertThat(preview.validationErrors()).isNotEmpty();
		verify(bindingMapper, never()).insert(any(OcpxTaskEventBinding.class));
	}

	private OcpxTask task() {
		OcpxTask task = new OcpxTask();
		task.setId("task-1");
		task.setAdvertiserId("advertiser-1");
		task.setChannelId("channel-1");
		task.setMediaAccountId("media-1");
		task.setProductId("product-1");
		task.setConfigVersion(1L);
		task.setStatus("DRAFT");
		return task;
	}

}
