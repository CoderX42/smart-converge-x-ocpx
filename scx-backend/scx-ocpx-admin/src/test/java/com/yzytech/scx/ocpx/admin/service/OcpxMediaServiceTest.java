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
			.create(new MediaRequests.Create(" 小米 ", " mi-signing ", "/mi/feedback", " __OAID__ "));

		assertThat(result.getMediaName()).isEqualTo("小米");
		assertThat(result.getCredentialConfigKey()).isEqualTo("mi-signing");
		assertThat(result.getUri()).isEqualTo("/mi/feedback");
		assertThat(result.getMacroParams()).isEqualTo("__OAID__");
		verify(refreshPublisher).mark();
	}

	@Test
	void optionsReturnIdAndNameForQuerySelect() {
		OcpxMedia media = new OcpxMedia();
		media.setId("media-1");
		media.setMediaName("小米");
		when(mediaMapper.selectList(any())).thenReturn(java.util.List.of(media));

		assertThat(service.options()).extracting(MediaViews.Option::getId, MediaViews.Option::getName)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("media-1", "小米"));
	}

	@Test
	void createRejectsDuplicateName() {
		when(mediaMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.create(new MediaRequests.Create("小米", null, "/feedback", null)))
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
