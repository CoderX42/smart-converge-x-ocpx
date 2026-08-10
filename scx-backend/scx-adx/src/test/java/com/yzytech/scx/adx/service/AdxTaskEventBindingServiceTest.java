package com.yzytech.scx.adx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.adx.dto.EventRequests;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxChannelEventMapping;
import com.yzytech.scx.adx.entity.AdxEventDefinition;
import com.yzytech.scx.adx.entity.AdxMediaAccount;
import com.yzytech.scx.adx.entity.AdxProduct;
import com.yzytech.scx.adx.entity.AdxTask;
import com.yzytech.scx.adx.entity.AdxTaskEventBinding;
import com.yzytech.scx.adx.mapper.AdxChannelEventMappingMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxEventChangeAuditMapper;
import com.yzytech.scx.adx.mapper.AdxEventDefinitionMapper;
import com.yzytech.scx.adx.mapper.AdxMediaAccountMapper;
import com.yzytech.scx.adx.mapper.AdxProductEventPolicyMapper;
import com.yzytech.scx.adx.mapper.AdxProductMapper;
import com.yzytech.scx.adx.mapper.AdxTaskEventBindingMapper;
import com.yzytech.scx.adx.mapper.AdxTaskMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class AdxTaskEventBindingServiceTest {

	@Mock
	private AdxTaskMapper taskMapper;

	@Mock
	private AdxTaskEventBindingMapper bindingMapper;

	@Mock
	private AdxEventDefinitionMapper definitionMapper;

	@Mock
	private AdxChannelEventMappingMapper mappingMapper;

	@Mock
	private AdxProductEventPolicyMapper productPolicyMapper;

	@Mock
	private AdxProductMapper productMapper;

	@Mock
	private AdxMediaAccountMapper mediaAccountMapper;

	@Mock
	private AdxChannelMapper channelMapper;

	@Mock
	private AdxEventChangeAuditMapper auditMapper;

	@Mock
	private AdxAccessScope accessScope;

	@Mock
	private AdxConfigChangeService configChangeService;

	private AdxTaskEventBindingService service;

	@BeforeEach
	void setUp() {
		service = new AdxTaskEventBindingService(taskMapper, bindingMapper, definitionMapper, mappingMapper,
				productPolicyMapper, productMapper, mediaAccountMapper, channelMapper, auditMapper, accessScope,
				configChangeService);
	}

	@Test
	void createOptionsRejectsIndependentlyScopedButCrossChannelParentIds() {
		AdxProduct product = new AdxProduct();
		product.setId("product-1");
		product.setAdvertiserId("advertiser-1");
		product.setStatus("ACTIVE");
		AdxMediaAccount account = new AdxMediaAccount();
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
		AdxTask task = task();
		task.setConfigVersion(3L);
		when(taskMapper.selectOne(any())).thenReturn(task);

		assertThatThrownBy(() -> service.replace("task-1", new EventRequests.ReplaceTaskBindings(2L, List.of())))
			.isInstanceOfSatisfying(BootBusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
		verify(accessScope).require(AdxAccessScope.ScopeType.ADVERTISER, "advertiser-1",
				AdxAccessScope.PermissionLevel.OPERATE);
		verify(accessScope).require(AdxAccessScope.ScopeType.CHANNEL, "channel-1",
				AdxAccessScope.PermissionLevel.OPERATE);
		verify(accessScope).require(AdxAccessScope.ScopeType.MEDIA_ACCOUNT, "media-1",
				AdxAccessScope.PermissionLevel.OPERATE);
		verify(bindingMapper, never()).insert(any(AdxTaskEventBinding.class));
	}

	@Test
	void rejectsCrossChannelMappingAndDoesNotWriteAnyBinding() {
		AdxTask task = task();
		AdxEventDefinition definition = new AdxEventDefinition();
		definition.setId("definition-1");
		definition.setMappingState("CANONICAL");
		definition.setStatus("ACTIVE");
		AdxChannelEventMapping mapping = new AdxChannelEventMapping();
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
		verify(bindingMapper, never()).insert(any(AdxTaskEventBinding.class));
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
		AdxTask task = task();
		task.setConfigVersion(7L);
		when(taskMapper.selectById("task-1")).thenReturn(task);
		when(bindingMapper.selectList(any())).thenReturn(List.of());

		var preview = service.previewSnapshot("task-1");

		assertThat(preview.schemaVersion()).isEqualTo(2);
		assertThat(preview.immutable()).isTrue();
		assertThat(preview.persisted()).isFalse();
		assertThat(preview.outboxCreated()).isFalse();
		verify(bindingMapper, never()).insert(any(AdxTaskEventBinding.class));
		verify(taskMapper, never()).updateById(any(AdxTask.class));
	}

	@Test
	void snapshotPreviewRejectsAReferencedDefinitionThatWasDisabledAfterDraftSave() {
		AdxTaskEventBinding binding = new AdxTaskEventBinding();
		binding.setId("binding-1");
		binding.setTaskId("task-1");
		binding.setEventDefinitionId("definition-1");
		binding.setChannelEventMappingId("mapping-1");
		binding.setCallbackRatioBasisPoints(10000);
		binding.setOverLimitDelivery(false);
		binding.setStatus("ACTIVE");
		AdxEventDefinition definition = new AdxEventDefinition();
		definition.setId("definition-1");
		definition.setMappingState("CANONICAL");
		definition.setStatus("DISABLED");
		AdxChannelEventMapping mapping = new AdxChannelEventMapping();
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
		verify(bindingMapper, never()).insert(any(AdxTaskEventBinding.class));
	}

	private AdxTask task() {
		AdxTask task = new AdxTask();
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
