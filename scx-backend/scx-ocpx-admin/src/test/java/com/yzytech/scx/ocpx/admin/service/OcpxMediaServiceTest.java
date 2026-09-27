package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.controller.OcpxMediaController;
import com.yzytech.scx.ocpx.admin.dto.MediaRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaViews;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxMediaServiceTest {

	private OcpxMediaMapper mediaMapper;

	private OcpxConfigRefreshPublisher refreshPublisher;

	private AdvertiserScope advertiserScope;

	private OcpxMediaService service;

	@BeforeEach
	void setUp() {
		mediaMapper = mock(OcpxMediaMapper.class);
		refreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxMediaService(mediaMapper, refreshPublisher, advertiserScope);
	}

	@Test
	void createPersistsOnlyVientianeAlignedFieldsAndReturnsCredentialReference() {
		when(mediaMapper.selectCount(any())).thenReturn(0L);
		AtomicReference<OcpxMedia> stored = new AtomicReference<>();
		doAnswer(invocation -> {
			OcpxMedia media = invocation.getArgument(0);
			media.setId("media-1");
			stored.set(media);
			return 1;
		}).when(mediaMapper).insert(any(OcpxMedia.class));
		when(mediaMapper.selectById("media-1")).thenAnswer(invocation -> stored.get());

		MediaViews.Item result = service
			.create(new MediaRequests.Create(" 示例媒体 ", " generic-media-ref ", "/feedback", " oaid=__OAID__ "));

		assertThat(result.getMediaName()).isEqualTo("示例媒体");
		assertThat(result.getCredentialConfigKey()).isEqualTo("generic-media-ref");
		assertThat(result.getUri()).isEqualTo("/feedback");
		assertThat(result.getMacroParams()).isEqualTo("oaid=__OAID__");
		verify(refreshPublisher).mark();
	}

	@Test
	void createAcceptsCanonicalCaidAndRejectsLegacyAlias() {
		when(mediaMapper.selectCount(any())).thenReturn(0L);
		AtomicReference<OcpxMedia> stored = new AtomicReference<>();
		doAnswer(invocation -> {
			OcpxMedia media = invocation.getArgument(0);
			media.setId("media-caid");
			stored.set(media);
			return 1;
		}).when(mediaMapper).insert(any(OcpxMedia.class));
		when(mediaMapper.selectById("media-caid")).thenAnswer(invocation -> stored.get());

		MediaViews.Item result = service
			.create(new MediaRequests.Create("CAID 媒体", null, "/feedback", "caid=__CAID__"));

		assertThat(result.getMacroParams()).isEqualTo("caid=__CAID__");
		assertThatThrownBy(() -> service.create(
				new MediaRequests.Create("旧字段媒体", null, "/feedback", "caid_list=__CAID_LIST__")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("兼容字段");
		when(mediaMapper.selectById("media-caid")).thenAnswer(invocation -> stored.get());
		MediaViews.Item advanced = service.create(
				new MediaRequests.Create("高级 CAID 媒体", null, "/feedback", "caid2=__CAID2__"));
		assertThat(advanced.getMacroParams()).isEqualTo("caid2=__CAID2__");
	}

	@Test
	void catalogKeepsLegacyCaidEntriesForReadOnlyCompatibilityButRemovesSystemRequestId() {
		MediaMacroCatalog catalog = new MediaMacroCatalog();

		assertThat(catalog.list()).extracting(MediaMacroCatalog.Macro::key)
			.contains("caid", "caid_list", "caid1", "caid2", "last_caid", "prev_caid", "tagid")
			.doesNotContain("request_id", "mts");
	}

	@Test
	void createRejectsRetiredMillisecondTimestampForNewMedia() {
		when(mediaMapper.selectCount(any())).thenReturn(0L);

		assertThatThrownBy(() -> service.create(
				new MediaRequests.Create("毫秒时间戳媒体", null, "/feedback", "mts=__MTS__")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("秒时间戳 ts");
	}

	@Test
	void createRejectsDuplicateMalformedAndSystemManagedMacroKeys() {
		when(mediaMapper.selectCount(any())).thenReturn(0L);
		AtomicReference<OcpxMedia> customStored = new AtomicReference<>();
		doAnswer(invocation -> {
			OcpxMedia media = invocation.getArgument(0);
			media.setId("custom-media");
			customStored.set(media);
			return 1;
		}).when(mediaMapper).insert(any(OcpxMedia.class));
		when(mediaMapper.selectById("custom-media")).thenAnswer(invocation -> customStored.get());

		assertThatThrownBy(() -> service.create(
				new MediaRequests.Create("无 key 媒体", null, "/feedback", "__CAID__")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("key=宏值");
		MediaViews.Item custom = service.create(
				new MediaRequests.Create("自定义媒体", null, "/feedback", "partner_token=__PARTNER_TOKEN__"));
		assertThat(custom.getMacroParams()).isEqualTo("partner_token=__PARTNER_TOKEN__");
		assertThatThrownBy(() -> service.create(
				new MediaRequests.Create("重复媒体", null, "/feedback", "caid=__CAID__&caid=__CAID__")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("重复");
		assertThatThrownBy(() -> service.create(
				new MediaRequests.Create("自定义重复媒体", null, "/feedback", "foo=1&foo=2")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("重复");
		assertThatThrownBy(() -> service.create(
				new MediaRequests.Create("系统媒体", null, "/feedback", "request_id=__REQUEST_ID__")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("系统自动");
	}

	@Test
	void editingLegacyMediaPreservesItsMacroParams() {
		OcpxMedia current = new OcpxMedia();
		current.setId("legacy-media");
		current.setMediaName("旧媒体");
		current.setUri("/feedback");
		current.setMacroParams("caid_list=__CAID_LIST__&caid1=__CAID1__&mts=__MTS__");
		when(mediaMapper.selectById("legacy-media")).thenReturn(current);
		when(mediaMapper.selectCount(any())).thenReturn(0L);
		AtomicReference<OcpxMedia> updated = new AtomicReference<>();
		doAnswer(invocation -> {
			updated.set(invocation.getArgument(0));
			return 1;
		}).when(mediaMapper).updateById(any(OcpxMedia.class));
		when(mediaMapper.selectById("legacy-media")).thenAnswer(invocation -> {
			OcpxMedia value = updated.get();
			if (value == null) {
				return current;
			}
			value.setMediaName("旧媒体");
			value.setUri("/feedback");
			return value;
		});

		MediaViews.Item result = service.update("legacy-media",
				new MediaRequests.Update("旧媒体", null, "/feedback", current.getMacroParams()));

		assertThat(result.getMacroParams()).isEqualTo(current.getMacroParams());
	}

	@Test
	void optionsReturnIdAndNameForQuerySelect() {
		OcpxMedia media = new OcpxMedia();
		media.setId("media-1");
		media.setMediaName("示例媒体");
		when(mediaMapper.selectList(any())).thenReturn(java.util.List.of(media));

		assertThat(service.options()).extracting(MediaViews.Option::getId, MediaViews.Option::getName)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("media-1", "示例媒体"));
	}

	@Test
	void createRejectsDuplicateName() {
		when(mediaMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.create(new MediaRequests.Create("示例媒体", null, "/feedback", null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("媒体名称已存在");
	}

	@Test
	void mediaOptionsAllowReadOnlyConsumersWithOrPermission() throws NoSuchMethodException {
		SaCheckPermission permission = OcpxMediaController.class.getMethod("options")
			.getAnnotation(SaCheckPermission.class);

		assertThat(permission.mode()).isEqualTo(SaMode.OR);
		assertThat(Arrays.asList(permission.value())).contains("ocpx:media:view", "ocpx:monitor-task:view",
				"ocpx:deduction-rule:list", "ocpx:report:view", "ocpx:dashboard:view");
	}

	@Test
	void deleteUnusedMediaPublishesRefresh() {
		OcpxMedia media = new OcpxMedia();
		media.setId("media-1");
		when(mediaMapper.selectById("media-1")).thenReturn(media);
		when(mediaMapper.countReferences("media-1")).thenReturn(0);
		when(mediaMapper.deleteById("media-1")).thenReturn(1);

		service.delete("media-1");

		verify(advertiserScope).requireAdministrator();
		verify(mediaMapper).deleteById("media-1");
		verify(refreshPublisher).mark();
	}

	@Test
	void deleteRejectsMediaWithBusinessOrHistoricalReferences() {
		OcpxMedia media = new OcpxMedia();
		media.setId("media-1");
		when(mediaMapper.selectById("media-1")).thenReturn(media);
		when(mediaMapper.countReferences("media-1")).thenReturn(1);

		assertThatThrownBy(() -> service.delete("media-1")).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("历史数据");
		verify(mediaMapper, never()).deleteById("media-1");
		verify(refreshPublisher, never()).mark();
	}

}
