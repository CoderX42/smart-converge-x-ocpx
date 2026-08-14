package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.MediaRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaViews;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxMediaServiceTest {

	private OcpxMediaMapper mediaMapper;

	private OcpxConfigRefreshPublisher refreshPublisher;

	private OcpxMediaService service;

	@BeforeEach
	void setUp() {
		mediaMapper = mock(OcpxMediaMapper.class);
		refreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		service = new OcpxMediaService(mediaMapper, refreshPublisher);
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

		assertThat(result.mediaName()).isEqualTo("小米");
		assertThat(result.credentialConfigKey()).isEqualTo("mi-signing");
		assertThat(result.uri()).isEqualTo("/mi/feedback");
		assertThat(result.macroParams()).isEqualTo("__OAID__");
		verify(refreshPublisher).mark();
	}

	@Test
	void optionsReturnIdAndNameForQuerySelect() {
		OcpxMedia media = new OcpxMedia();
		media.setId("media-1");
		media.setMediaName("小米");
		when(mediaMapper.selectList(any())).thenReturn(java.util.List.of(media));

		assertThat(service.options()).extracting(MediaViews.Option::id, MediaViews.Option::name)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("media-1", "小米"));
	}

	@Test
	void createRejectsDuplicateName() {
		when(mediaMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.create(new MediaRequests.Create("小米", null, "/feedback", null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("媒体名称已存在");
	}

}
