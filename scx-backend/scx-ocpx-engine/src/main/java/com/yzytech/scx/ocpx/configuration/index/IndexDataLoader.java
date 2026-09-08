package com.yzytech.scx.ocpx.configuration.index;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxAdvertiserConfigMapper;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxMediaConfigMapper;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxMonitorTaskConfigMapper;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxPromotionLinkConfigMapper;
import com.yzytech.scx.ocpx.configuration.mapper.OcpxProtocolAllowedHostConfigMapper;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 从数据库全量加载 OCPX 业务实体及不含密钥的 RTA 运行配置，并刷新 Engine 内存索引。 */
@Slf4j
@Component
public class IndexDataLoader {

	private final OcpxAdvertiserConfigMapper advertiserMapper;

	private final OcpxPromotionLinkConfigMapper promotionLinkMapper;

	private final OcpxMediaConfigMapper mediaMapper;

	private final OcpxMonitorTaskConfigMapper monitorTaskMapper;

	private final OcpxProtocolAllowedHostConfigMapper protocolAllowedHostMapper;

	private final OcpxAdvertiserIndex advertiserIndex;

	private final OcpxPromotionLinkIndex promotionLinkIndex;

	private final OcpxMediaIndex mediaIndex;

	private final OcpxMonitorTaskIndex monitorTaskIndex;

	private final OcpxProtocolAllowedHostIndex protocolAllowedHostIndex;


	@Autowired
	public IndexDataLoader(OcpxAdvertiserConfigMapper advertiserMapper,
			OcpxPromotionLinkConfigMapper promotionLinkMapper, OcpxMediaConfigMapper mediaMapper,
			OcpxMonitorTaskConfigMapper monitorTaskMapper,
			OcpxProtocolAllowedHostConfigMapper protocolAllowedHostMapper, OcpxAdvertiserIndex advertiserIndex,
			OcpxPromotionLinkIndex promotionLinkIndex, OcpxMediaIndex mediaIndex, OcpxMonitorTaskIndex monitorTaskIndex,
			OcpxProtocolAllowedHostIndex protocolAllowedHostIndex) {
		this.advertiserMapper = advertiserMapper;
		this.promotionLinkMapper = promotionLinkMapper;
		this.mediaMapper = mediaMapper;
		this.monitorTaskMapper = monitorTaskMapper;
		this.protocolAllowedHostMapper = protocolAllowedHostMapper;
		this.advertiserIndex = advertiserIndex;
		this.promotionLinkIndex = promotionLinkIndex;
		this.mediaIndex = mediaIndex;
		this.monitorTaskIndex = monitorTaskIndex;
		this.protocolAllowedHostIndex = protocolAllowedHostIndex;
	}

	/**
	 * 清空旧快照并按广告主、链接、媒体、任务顺序重新装载。
	 *
	 * <p>
	 * 当前刷新不是跨四个 Map 的原子切换，刷新窗口内调用方可能短暂读到部分新快照。
	 */
	public void init() {
		log.info("IndexDataLoader start");
		advertiserIndex.clear();
		advertiserMapper.selectList(null).forEach(item -> advertiserIndex.add(item.getId(), item));
		promotionLinkIndex.clear();
		promotionLinkMapper.selectList(null).forEach(item -> promotionLinkIndex.add(item.getId(), item));
		mediaIndex.clear();
		mediaMapper.selectList(null).forEach(item -> mediaIndex.add(item.getId(), item));
		monitorTaskIndex.clear();
		monitorTaskMapper.selectList(null).forEach(item -> monitorTaskIndex.add(item.getId(), item));
		Map<OcpxUpstreamProtocol, Set<String>> allowedHosts = new EnumMap<>(OcpxUpstreamProtocol.class);
		protocolAllowedHostMapper.selectList(null)
			.forEach(item -> allowedHosts.computeIfAbsent(item.getProtocolType(), ignored -> new LinkedHashSet<>())
				.add(item.getHost()));
		protocolAllowedHostIndex.replace(allowedHosts);
		log.info("IndexDataLoader end");
	}

}
