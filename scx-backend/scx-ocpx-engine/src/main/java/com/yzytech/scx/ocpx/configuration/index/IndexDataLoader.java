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
import org.springframework.jdbc.core.JdbcTemplate;
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

	private final JdbcTemplate jdbcTemplate;

	private final RtaConfigIndex rtaConfigIndex;

	@Autowired
	public IndexDataLoader(OcpxAdvertiserConfigMapper advertiserMapper,
			OcpxPromotionLinkConfigMapper promotionLinkMapper, OcpxMediaConfigMapper mediaMapper,
			OcpxMonitorTaskConfigMapper monitorTaskMapper, OcpxProtocolAllowedHostConfigMapper protocolAllowedHostMapper,
			OcpxAdvertiserIndex advertiserIndex, OcpxPromotionLinkIndex promotionLinkIndex, OcpxMediaIndex mediaIndex,
			OcpxMonitorTaskIndex monitorTaskIndex, OcpxProtocolAllowedHostIndex protocolAllowedHostIndex,
			JdbcTemplate jdbcTemplate, RtaConfigIndex rtaConfigIndex) {
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
		this.jdbcTemplate = jdbcTemplate;
		this.rtaConfigIndex = rtaConfigIndex;
	}

	/** Backward-compatible constructor for index unit tests that do not provision a database. */
	public IndexDataLoader(OcpxAdvertiserConfigMapper advertiserMapper,
			OcpxPromotionLinkConfigMapper promotionLinkMapper, OcpxMediaConfigMapper mediaMapper,
			OcpxMonitorTaskConfigMapper monitorTaskMapper, OcpxProtocolAllowedHostConfigMapper protocolAllowedHostMapper,
			OcpxAdvertiserIndex advertiserIndex, OcpxPromotionLinkIndex promotionLinkIndex, OcpxMediaIndex mediaIndex,
			OcpxMonitorTaskIndex monitorTaskIndex, OcpxProtocolAllowedHostIndex protocolAllowedHostIndex) {
		this(advertiserMapper, promotionLinkMapper, mediaMapper, monitorTaskMapper, protocolAllowedHostMapper,
				advertiserIndex, promotionLinkIndex, mediaIndex, monitorTaskIndex, protocolAllowedHostIndex, null, null);
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
		if (jdbcTemplate != null && rtaConfigIndex != null) {
			try {
				loadRtaIndex();
			}
			catch (RuntimeException exception) {
				// V128 is forward-only; an instance can start before the optional RTA migration is applied.
				// RTA requests still fail closed through their own storage checks.
				rtaConfigIndex.replace(Map.of());
				log.warn("RTA configuration index unavailable type:{}", exception.getClass().getSimpleName());
			}
		}
		log.info("IndexDataLoader end");
	}

	private void loadRtaIndex() {
		Map<String, RtaSnapshotBuilder> builders = new LinkedHashMap<>();
		jdbcTemplate.query("""
				SELECT t.id,t.rta_enabled,b.profile_id,p.adapter_code,p.config_json,b.external_task_id
				FROM scx_ocpx_monitor_task t
				LEFT JOIN scx_ocpx_rta_task_binding b ON b.monitor_task_id=t.id AND b.enabled=1
				LEFT JOIN scx_ocpx_rta_profile p ON p.id=b.profile_id AND p.status='ACTIVE'
				WHERE t.is_delete=0
				""", (rs, rowNum) -> {
			String taskId = rs.getString("id");
			boolean enabled = rs.getBoolean("rta_enabled");
			RtaSnapshotBuilder builder = builders.computeIfAbsent(taskId,
					ignored -> new RtaSnapshotBuilder(enabled));
			String profileId = rs.getString("profile_id");
			if (profileId != null && builder.profileId == null) {
				builder.profileId = profileId;
				builder.adapterCode = rs.getString("adapter_code");
				builder.configJson = sanitizeMockConfig(rs.getString("config_json"));
			}
			String externalTaskId = rs.getString("external_task_id");
			if (profileId != null && externalTaskId != null) {
				builder.externalTaskIds.add(externalTaskId);
			}
			return taskId;
		});
		Map<String, RtaConfigIndex.Snapshot> snapshots = new LinkedHashMap<>();
		builders.forEach((taskId, builder) -> snapshots.put(taskId,
				new RtaConfigIndex.Snapshot(builder.enabled, builder.profileId, builder.adapterCode,
						builder.configJson, List.copyOf(builder.externalTaskIds))));
		rtaConfigIndex.replace(snapshots);
	}

	/** Keep only Mock controls in the process snapshot; provider credentials never enter memory. */
	private String sanitizeMockConfig(String value) {
		if (value == null || value.isBlank()) {
			return "{}";
		}
		try {
			JSONObject source = JSON.parseObject(value);
			JSONObject safe = new JSONObject();
			if (source.containsKey("decision")) {
				safe.put("decision", source.getString("decision"));
			}
			if (source.containsKey("delayMs")) {
				safe.put("delayMs", Math.max(0, Math.min(10_000, source.getLongValue("delayMs"))));
			}
			if (source.containsKey("taskIds")) {
				List<String> taskIds = source.getList("taskIds", String.class);
				if (taskIds != null) {
					safe.put("taskIds", taskIds.stream().filter(item -> item != null && item.length() <= 128).distinct()
						.limit(100).toList());
				}
			}
			return JSON.toJSONString(safe);
		}
		catch (RuntimeException exception) {
			return "{}";
		}
	}

	private static final class RtaSnapshotBuilder {

		private final boolean enabled;

		private String profileId;

		private String adapterCode;

		private String configJson;

		private final List<String> externalTaskIds = new ArrayList<>();

		private RtaSnapshotBuilder(boolean enabled) {
			this.enabled = enabled;
		}

	}

}
