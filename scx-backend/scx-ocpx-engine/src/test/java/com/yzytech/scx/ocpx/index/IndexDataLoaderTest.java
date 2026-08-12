package com.yzytech.scx.ocpx.index;

import com.yzytech.scx.ocpx.dao.mapper.OcpxAdvertiserConfigMapper;
import com.yzytech.scx.ocpx.dao.mapper.OcpxMediaConfigMapper;
import com.yzytech.scx.ocpx.dao.mapper.OcpxMonitorTaskConfigMapper;
import com.yzytech.scx.ocpx.dao.mapper.OcpxPromotionLinkConfigMapper;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndexDataLoaderTest {

	private final UserDspIndex advertisers = new UserDspIndex();

	private final DspLinkIndex links = new DspLinkIndex();

	private final UserChannelIndex media = new UserChannelIndex();

	private final ChannelMonitorIndex tasks = new ChannelMonitorIndex();

	@AfterEach
	void clearIndexes() {
		advertisers.clear();
		links.clear();
		media.clear();
		tasks.clear();
	}

	@Test
	void loadsAllFourScxEntitiesByTheirStringPrimaryKeys() {
		OcpxAdvertiserConfigMapper advertiserMapper = mock(OcpxAdvertiserConfigMapper.class);
		OcpxPromotionLinkConfigMapper linkMapper = mock(OcpxPromotionLinkConfigMapper.class);
		OcpxMediaConfigMapper mediaMapper = mock(OcpxMediaConfigMapper.class);
		OcpxMonitorTaskConfigMapper taskMapper = mock(OcpxMonitorTaskConfigMapper.class);
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-id");
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-id");
		OcpxMedia channel = new OcpxMedia();
		channel.setId("media-id");
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-id");
		when(advertiserMapper.selectList(null)).thenReturn(List.of(advertiser));
		when(linkMapper.selectList(null)).thenReturn(List.of(link));
		when(mediaMapper.selectList(null)).thenReturn(List.of(channel));
		when(taskMapper.selectList(null)).thenReturn(List.of(task));

		new IndexDataLoader(advertiserMapper, linkMapper, mediaMapper, taskMapper, advertisers, links, media, tasks)
			.init();

		assertThat(advertisers.get("advertiser-id")).isSameAs(advertiser);
		assertThat(links.get("link-id")).isSameAs(link);
		assertThat(media.get("media-id")).isSameAs(channel);
		assertThat(tasks.get("task-id")).isSameAs(task);
	}

}
