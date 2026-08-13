package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcpxMediaServiceTest {

	private OcpxMediaMapper mediaMapper;

	private OcpxMediaService service;

	@BeforeEach
	void setUp() {
		mediaMapper = mock(OcpxMediaMapper.class);
		service = new OcpxMediaService(mediaMapper, mock(OcpxMonitorTaskMapper.class), mock(OcpxUrlPolicy.class),
				mock(OcpxConfigRefreshPublisher.class));
	}

	@Test
	void exportsCsvWithChannelColumns() {
		when(mediaMapper.selectList(any())).thenReturn(List.of(media("media-1", "小米"), media("media-2", "华为")));

		String csv = service.exportCsv(null, null, null);

		String[] lines = csv.split("\n");
		assertThat(lines).hasSize(3);
		assertThat(lines[0]).isEqualTo("媒体ID,媒体名称,'uri','宏参数',创建时间,修改时间");
		assertThat(lines[1]).isEqualTo("media-1,小米,/ocpx/mi/feedback,__CID__,2026-08-12 01:02:03,2026-08-13 02:03:04");
		assertThat(lines[2]).startsWith("media-2,华为,");
	}

	@Test
	void exportsEmptyCsvWhenNoMedia() {
		when(mediaMapper.selectList(any())).thenReturn(List.of());

		assertThat(service.exportCsv("不存在", null, null)).isEmpty();
	}

	private OcpxMedia media(String id, String name) {
		OcpxMedia media = new OcpxMedia();
		media.setId(id);
		media.setMediaName(name);
		media.setIngressUri("/ocpx/mi/feedback");
		media.setMacroParams("__CID__");
		media.setCreateTime(LocalDateTime.of(2026, 8, 12, 1, 2, 3));
		media.setUpdateTime(LocalDateTime.of(2026, 8, 13, 2, 3, 4));
		return media;
	}

}
