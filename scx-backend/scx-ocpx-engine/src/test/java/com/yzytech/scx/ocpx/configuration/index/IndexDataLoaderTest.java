package com.yzytech.scx.ocpx.configuration.index;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxAdvertiserConfigMapper;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxMediaConfigMapper;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxMonitorTaskConfigMapper;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxPromotionLinkConfigMapper;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxProtocolAllowedHostConfigMapper;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.entity.OcpxProtocolAllowedHost;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndexDataLoaderTest {

	private final OcpxAdvertiserIndex advertisers = new OcpxAdvertiserIndex();

	private final OcpxPromotionLinkIndex links = new OcpxPromotionLinkIndex();

	private final OcpxMediaIndex media = new OcpxMediaIndex();

	private final OcpxMonitorTaskIndex tasks = new OcpxMonitorTaskIndex();

	private final OcpxProtocolAllowedHostIndex protocolHosts = new OcpxProtocolAllowedHostIndex();

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
		OcpxProtocolAllowedHostConfigMapper protocolHostMapper = mock(OcpxProtocolAllowedHostConfigMapper.class);
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-id");
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-id");
		OcpxMedia channel = new OcpxMedia();
		channel.setId("media-id");
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-id");
		OcpxProtocolAllowedHost protocolHost = new OcpxProtocolAllowedHost();
		protocolHost.setProtocolType(OcpxUpstreamProtocol.AERIM);
		protocolHost.setHost("track.example.com");
		when(advertiserMapper.selectList(null)).thenReturn(List.of(advertiser));
		when(linkMapper.selectList(null)).thenReturn(List.of(link));
		when(mediaMapper.selectList(null)).thenReturn(List.of(channel));
		when(taskMapper.selectList(null)).thenReturn(List.of(task));
		when(protocolHostMapper.selectList(null)).thenReturn(List.of(protocolHost));

		new IndexDataLoader(advertiserMapper, linkMapper, mediaMapper, taskMapper, protocolHostMapper, advertisers,
				links, media, tasks, protocolHosts)
			.init();

		assertThat(advertisers.get("advertiser-id")).isSameAs(advertiser);
		assertThat(links.get("link-id")).isSameAs(link);
		assertThat(media.get("media-id")).isSameAs(channel);
		assertThat(tasks.get("task-id")).isSameAs(task);
		assertThat(protocolHosts.get(OcpxUpstreamProtocol.AERIM)).containsExactly("track.example.com");
	}

	/**
	 * 只有推广链接和监测任务保留逻辑删除；广告主和媒体的 V102 模型没有删除字段。
	 */
	@Test
	void reliesOnTableLogicToExcludeLogicallyDeletedRows() throws NoSuchFieldException {
		for (Class<?> entity : List.of(OcpxPromotionLink.class, OcpxMonitorTask.class)) {
			Field deleted = entity.getDeclaredField("deleted");
			assertThat(deleted.isAnnotationPresent(TableLogic.class)).as(entity.getSimpleName()).isTrue();
		}
	}

}
