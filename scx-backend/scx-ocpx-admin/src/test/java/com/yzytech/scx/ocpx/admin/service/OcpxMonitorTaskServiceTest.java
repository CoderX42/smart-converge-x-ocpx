package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.controller.OcpxMonitorTaskController;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskViews;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxMonitorTaskServiceTest {

	private OcpxMonitorTaskMapper taskMapper;

	private OcpxAdvertiserMapper advertiserMapper;

	private OcpxPromotionLinkMapper linkMapper;

	private OcpxMediaMapper mediaMapper;

	private OcpxTrackingProperties trackingProperties;

	private OcpxConfigRefreshPublisher configRefreshPublisher;

	private AdvertiserScope advertiserScope;

	private OcpxMonitorTaskService service;

	@BeforeEach
	void setUp() {
		taskMapper = mock(OcpxMonitorTaskMapper.class);
		advertiserMapper = mock(OcpxAdvertiserMapper.class);
		linkMapper = mock(OcpxPromotionLinkMapper.class);
		mediaMapper = mock(OcpxMediaMapper.class);
		trackingProperties = new OcpxTrackingProperties();
		trackingProperties.setEngineBaseUrl("http://127.0.0.1:10000/ocpx");
		configRefreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxMonitorTaskService(taskMapper, advertiserMapper, linkMapper, mediaMapper, trackingProperties,
				configRefreshPublisher, advertiserScope);
	}

	@Test
	void createGeneratesAndPersistsImpAndClkUrls() {
		stubActiveRelations();
		when(taskMapper.selectCount(any())).thenReturn(0L);
		AtomicReference<OcpxMonitorTask> stored = new AtomicReference<>();
		doAnswer(invocation -> {
			OcpxMonitorTask task = invocation.getArgument(0);
			task.setId("task-1");
			stored.set(task);
			return 1;
		}).when(taskMapper).insert(any(OcpxMonitorTask.class));
		when(taskMapper.selectById(anyString())).thenAnswer(invocation -> stored.get());

		MonitorTaskViews.Item item = service.create(new MonitorTaskRequests.Create("任务一", "adv-1", "link-1", "media-1",
				new BigDecimal("12.340000"), "biz-1", "备注一"));

		String expectedShow = "http://127.0.0.1:10000/ocpx/feedback?"
				+ "request_id=__REQUEST_ID__&imei=__IMEI__&callback_url=__CALLBACK_URL__"
				+ "&event=IMP&mId=media-1&tId=task-1&aId=adv-1&lId=link-1";
		String expectedClk = "http://127.0.0.1:10000/ocpx/feedback?"
				+ "request_id=__REQUEST_ID__&imei=__IMEI__&callback_url=__CALLBACK_URL__"
				+ "&event=CLK&mId=media-1&tId=task-1&aId=adv-1&lId=link-1";
		ArgumentCaptor<OcpxMonitorTask> captor = ArgumentCaptor.forClass(OcpxMonitorTask.class);
		verify(taskMapper).updateById(captor.capture());
		assertThat(captor.getValue().getShowUrl()).isEqualTo(expectedShow);
		assertThat(captor.getValue().getClkUrl()).isEqualTo(expectedClk);
		assertThat(stored.get().getUnitPrice()).isEqualByComparingTo("12.340000");
		assertThat(stored.get().getBizId()).isEqualTo("biz-1");
		assertThat(stored.get().getRemark()).isEqualTo("备注一");
		assertThat(item.getShowUrl()).isEqualTo(expectedShow);
		assertThat(item.getClkUrl()).isEqualTo(expectedClk);
		verify(configRefreshPublisher).mark();
	}

	@Test
	void createRejectsMissingAdvertiserAndLinkInsteadOfPersistingDanglingTask() {
		when(mediaMapper.selectById("media-1")).thenReturn(media("media-1"));
		when(advertiserMapper.selectById(anyString())).thenReturn(null);
		when(linkMapper.selectById(anyString())).thenReturn(null);

		assertThatThrownBy(() -> service
			.create(new MonitorTaskRequests.Create("任务一", "adv-1", "link-1", "media-1", BigDecimal.ONE, null, null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("广告主不存在");
		verify(taskMapper, never()).insert(any(OcpxMonitorTask.class));
		verify(configRefreshPublisher, never()).mark();
	}

	@Test
	void createWithoutMacroParamsStillPrependsRequestIdMacro() {
		stubActiveRelations();
		when(taskMapper.selectCount(any())).thenReturn(0L);
		OcpxMedia media = media("media-1");
		media.setMacroParams(null);
		when(mediaMapper.selectById("media-1")).thenReturn(media);
		AtomicReference<OcpxMonitorTask> stored = new AtomicReference<>();
		doAnswer(invocation -> {
			OcpxMonitorTask task = invocation.getArgument(0);
			task.setId("task-1");
			stored.set(task);
			return 1;
		}).when(taskMapper).insert(any(OcpxMonitorTask.class));
		when(taskMapper.selectById(anyString())).thenAnswer(invocation -> stored.get());

		service
			.create(new MonitorTaskRequests.Create("任务一", "adv-1", "link-1", "media-1", BigDecimal.ZERO, null, null));

		ArgumentCaptor<OcpxMonitorTask> captor = ArgumentCaptor.forClass(OcpxMonitorTask.class);
		verify(taskMapper).updateById(captor.capture());
		assertThat(captor.getValue().getShowUrl())
			.isEqualTo("http://127.0.0.1:10000/ocpx/feedback?request_id=__REQUEST_ID__"
					+ "&event=IMP&mId=media-1&tId=task-1&aId=adv-1&lId=link-1");
		assertThat(captor.getValue().getClkUrl())
			.isEqualTo("http://127.0.0.1:10000/ocpx/feedback?request_id=__REQUEST_ID__"
					+ "&event=CLK&mId=media-1&tId=task-1&aId=adv-1&lId=link-1");
	}

	@Test
	void createReplacesDuplicateRequestIdParametersWithOneLeadingMacro() {
		stubActiveRelations();
		when(taskMapper.selectCount(any())).thenReturn(0L);
		OcpxMedia media = media("media-1");
		media.setMacroParams("imei=__IMEI__&request_id=fixed&callback_url=__CALLBACK_URL__"
				+ "&request_id=__REQUEST_ID__");
		when(mediaMapper.selectById("media-1")).thenReturn(media);
		doAnswer(invocation -> {
			OcpxMonitorTask task = invocation.getArgument(0);
			task.setId("task-1");
			return 1;
		}).when(taskMapper).insert(any(OcpxMonitorTask.class));
		when(taskMapper.selectById("task-1")).thenReturn(task("task-1", "adv-1", "link-1", "media-1"));

		service
			.create(new MonitorTaskRequests.Create("任务一", "adv-1", "link-1", "media-1", BigDecimal.ZERO, null, null));

		ArgumentCaptor<OcpxMonitorTask> captor = ArgumentCaptor.forClass(OcpxMonitorTask.class);
		verify(taskMapper).updateById(captor.capture());
		assertThat(captor.getValue().getShowUrl())
			.startsWith("http://127.0.0.1:10000/ocpx/feedback?request_id=__REQUEST_ID__&imei=__IMEI__")
			.containsOnlyOnce("request_id=");
	}

	@Test
	void updateRegeneratesUrlsAfterRebind() {
		OcpxMonitorTask current = task("task-1", "adv-1", "link-1", "media-1");
		when(taskMapper.selectById("task-1")).thenReturn(current);
		stubActiveRelations();
		when(mediaMapper.selectById("media-2")).thenReturn(media("media-2"));
		when(taskMapper.updateById(any(OcpxMonitorTask.class))).thenReturn(1);

		service.update("task-1", new MonitorTaskRequests.Update("任务一", "adv-1", "link-2", "media-2",
				new BigDecimal("9.9"), "biz-2", null));

		ArgumentCaptor<OcpxMonitorTask> captor = ArgumentCaptor.forClass(OcpxMonitorTask.class);
		verify(taskMapper).updateById(captor.capture());
		OcpxMonitorTask update = captor.getValue();
		assertThat(update.getShowUrl()).isEqualTo(
				"http://127.0.0.1:10000/ocpx/feedback?"
						+ "request_id=__REQUEST_ID__&imei=__IMEI__&callback_url=__CALLBACK_URL__"
						+ "&event=IMP&mId=media-2&tId=task-1&aId=adv-1&lId=link-2");
		assertThat(update.getClkUrl()).isEqualTo(
				"http://127.0.0.1:10000/ocpx/feedback?"
						+ "request_id=__REQUEST_ID__&imei=__IMEI__&callback_url=__CALLBACK_URL__"
						+ "&event=CLK&mId=media-2&tId=task-1&aId=adv-1&lId=link-2");
		assertThat(update.getUnitPrice()).isEqualByComparingTo("9.9");
		assertThat(update.getBizId()).isEqualTo("biz-2");
		verify(configRefreshPublisher).mark();
	}

	@Test
	void unitPriceIsRequiredAndNonNegative() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

		assertThat(validator
			.validate(new MonitorTaskRequests.Create("任务一", "adv-1", "link-1", "media-1", null, null, null)))
			.anyMatch(violation -> "unitPrice".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(new MonitorTaskRequests.Create("任务一", "adv-1", "link-1", "media-1",
				new BigDecimal("-0.01"), null, null)))
			.anyMatch(violation -> "unitPrice".equals(violation.getPropertyPath().toString()));
		assertThat(validator
			.validate(new MonitorTaskRequests.Create("任务一", "adv-1", "link-1", "media-1", BigDecimal.ZERO, null, null)))
			.noneMatch(violation -> "unitPrice".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(new MonitorTaskRequests.Create("任务一", "adv-1", "link-1", "media-1",
				new BigDecimal("1.0000001"), null, null)))
			.anyMatch(violation -> "unitPrice".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(
				new MonitorTaskRequests.Update("任务一", "adv-1", "link-1", "media-1", new BigDecimal("-1"), null, null)))
			.anyMatch(violation -> "unitPrice".equals(violation.getPropertyPath().toString()));
	}

	@Test
	void optionsReturnTasksForMediaCascade() {
		OcpxMonitorTask current = task("task-1", "adv-1", "link-1", "media-1");
		when(taskMapper.selectList(any())).thenReturn(java.util.List.of(current));

		assertThat(service.options("media-1"))
			.extracting(MonitorTaskViews.Option::getId, MonitorTaskViews.Option::getName)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("task-1", "任务一"));
	}

	@Test
	void deleteLogicallyDeletesTaskAndMarksRefresh() {
		OcpxMonitorTask current = task("task-1", "adv-1", "link-1", "media-1");
		when(taskMapper.selectById("task-1")).thenReturn(current);

		service.delete("task-1");

		// @TableLogic 使 deleteById 走逻辑删，删除后 selectById/selectPage 不可见
		verify(taskMapper).deleteById("task-1");
		verify(configRefreshPublisher).mark();
	}

	@Test
	void deleteRejectsMissingTask() {
		when(taskMapper.selectById(anyString())).thenReturn(null);

		assertThatThrownBy(() -> service.delete("task-x")).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("监测任务不存在");
		verify(taskMapper, never()).deleteById(anyString());
	}

	@Test
	void deleteEndpointCarriesPermissionAnnotation() throws NoSuchMethodException {
		var method = OcpxMonitorTaskController.class.getMethod("delete", String.class);

		SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
		assertThat(permission).isNotNull();
		assertThat(Arrays.asList(permission.value())).contains("ocpx:monitor-task:delete");
		assertThat(method.getAnnotation(DeleteMapping.class).value()).contains("/{id}");
	}

	@Test
	void updateRequiresBillingPermissionOnlyWhenTheBillingSnapshotChanges() throws NoSuchMethodException {
		var method = OcpxMonitorTaskController.class.getMethod("update", String.class,
				MonitorTaskRequests.Update.class);
		SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
		assertThat(permission.value()).containsExactly("ocpx:monitor-task:edit");
		assertThat(method.getAnnotation(PutMapping.class).value()).contains("/{id}");

		OcpxMonitorTask current = task("task-1", "adv-1", "link-1", "media-1");
		current.setUnitPrice(new BigDecimal("2.500000"));
		current.setConversionEvent("ACTIVATE");
		when(taskMapper.selectById("task-1")).thenReturn(current);
		stubActiveRelations();
		when(taskMapper.updateById(any(OcpxMonitorTask.class))).thenReturn(1);

		service.update("task-1", new MonitorTaskRequests.Update("任务改名", "adv-1", "link-1", "media-1",
				new BigDecimal("2.500000"), "ACTIVATE", null, null));
		verify(advertiserScope, never()).requireSystemAdministratorPermission(anyString());

		service.update("task-1", new MonitorTaskRequests.Update("任务改名", "adv-1", "link-1", "media-1",
				new BigDecimal("3.000000"), "PAYMENT", null, null));
		verify(advertiserScope).requireSystemAdministratorPermission("ocpx:monitor-task:billing");
	}

	private void stubActiveRelations() {
		when(advertiserMapper.selectCount(any())).thenReturn(1L);
		when(linkMapper.selectCount(any())).thenReturn(1L);
		when(mediaMapper.selectCount(any())).thenReturn(1L);
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser());
		when(linkMapper.selectById(anyString())).thenAnswer(invocation -> link(invocation.getArgument(0)));
		when(mediaMapper.selectById("media-1")).thenReturn(media("media-1"));
	}

	private OcpxMonitorTask task(String id, String advertiserId, String linkId, String mediaId) {
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId(id);
		task.setMonitorName("任务一");
		task.setAdvertiserId(advertiserId);
		task.setPromotionLinkId(linkId);
		task.setMediaId(mediaId);
		task.setDeleted(false);
		return task;
	}

	private OcpxAdvertiser advertiser() {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("adv-1");
		advertiser.setAdvertiserName("广告主一");
		return advertiser;
	}

	private OcpxPromotionLink link(String id) {
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId(id);
		link.setLinkName("链接一");
		return link;
	}

	private OcpxMedia media(String id) {
		OcpxMedia media = new OcpxMedia();
		media.setId(id);
		media.setMediaName("媒体一");
		media.setUri("/feedback");
		media.setMacroParams("imei=__IMEI__&request_id=__REQUEST_ID__&callback_url=__CALLBACK_URL__");
		return media;
	}

}
