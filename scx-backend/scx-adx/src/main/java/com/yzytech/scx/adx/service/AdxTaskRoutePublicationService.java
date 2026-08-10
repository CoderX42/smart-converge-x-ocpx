package com.yzytech.scx.adx.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.adx.config.AdxTrackingProperties;
import com.yzytech.scx.adx.dto.RoutePublishRequests;
import com.yzytech.scx.adx.dto.RoutePublishViews;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxAppLink;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxMediaAccount;
import com.yzytech.scx.adx.entity.AdxMediaCredential;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.entity.AdxProduct;
import com.yzytech.scx.adx.entity.AdxTask;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxAppLinkMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxMediaAccountMapper;
import com.yzytech.scx.adx.mapper.AdxMediaCredentialMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import com.yzytech.scx.adx.mapper.AdxProductMapper;
import com.yzytech.scx.adx.mapper.AdxTaskMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.contract.RouteEventSnapshot;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdxTaskRoutePublicationService {

	private static final int ROUTE_SCHEMA_VERSION = 3;

	private static final int EVENT_SNAPSHOT_SCHEMA_VERSION = 2;

	private static final String SINGLE_ENTERPRISE_TENANT = "SCX";

	private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

	private final AdxTaskMapper taskMapper;

	private final AdxMonitorLinkMapper monitorMapper;

	private final AdxAdvertiserMapper advertiserMapper;

	private final AdxProductMapper productMapper;

	private final AdxAppLinkMapper appLinkMapper;

	private final AdxChannelMapper channelMapper;

	private final AdxMediaAccountMapper mediaAccountMapper;

	private final AdxMediaCredentialMapper mediaCredentialMapper;

	private final AdxAccessScope accessScope;

	private final AdxAdapterService adapterService;

	private final TrackingRoutePolicy trackingPolicy;

	private final AdxTrackingProperties properties;

	private final ProtocolCrypto protocolCrypto;

	private final ObjectMapper objectMapper;

	private final JdbcTemplate jdbcTemplate;

	public RoutePublishViews.CheckResult check(String taskId) {
		Context context = context(requireTask(taskId, false, AdxAccessScope.PermissionLevel.OPERATE));
		List<RoutePublishViews.CheckItem> items = checks(context);
		String fingerprint = fingerprint(context);
		long expiresEpoch = Instant.now().plusSeconds(properties.getPublishCheckTtlSeconds()).getEpochSecond();
		String token = protocolCrypto.signToken("ADX_ROUTE_PUBLISH_CHECK", String.join("|", taskId,
				String.valueOf(version(context.task())), fingerprint, String.valueOf(expiresEpoch)));
		return new RoutePublishViews.CheckResult(taskId, version(context.task()), overall(items),
				LocalDateTime.ofInstant(Instant.ofEpochSecond(expiresEpoch), ZONE), token, summary(context), items);
	}

	@Transactional
	public RoutePublishViews.PublishResult publish(String taskId, RoutePublishRequests.Publish request) {
		RoutePublishViews.PublishResult existing = existingPublish(request.publishRequestId(), taskId);
		if (existing != null) {
			return existing;
		}
		AdxTask task = requireTask(taskId, true, AdxAccessScope.PermissionLevel.OPERATE);
		existing = existingPublish(request.publishRequestId(), taskId);
		if (existing != null) {
			return existing;
		}
		if (version(task) != request.expectedTaskVersion()) {
			throw conflict("ADX_TASK_VERSION_CONFLICT：发布前任务已变化，请重新检查");
		}
		if (!Boolean.TRUE.equals(task.getRepublishRequired())
				&& version(task.getPublishedTaskVersion()) == version(task)) {
			throw conflict("ADX_TASK_REVISION_ALREADY_PUBLISHED：当前 Task revision 已发布，请勿更换幂等键重复创建版本");
		}
		Context context = context(task);
		List<RoutePublishViews.CheckItem> items = checks(context);
		String currentFingerprint = fingerprint(context);
		verifyCheckToken(request.checkToken(), taskId, request.expectedTaskVersion(), currentFingerprint);
		if (items.stream().anyMatch(item -> "FAIL".equals(item.status()))) {
			throw validation("ADX_PUBLISH_VALIDATION_FAILED：发布检查仍有阻塞项");
		}
		if (!List.of("READY", "PAUSED").contains(task.getStatus())) {
			throw validation("ADX_TASK_NOT_READY：只有 READY 或经重新检查的 PAUSED Task 可以发布");
		}
		List<Published> published = new ArrayList<>();
		published.add(publishRoute(context, "CLICK", task.getClickAppLinkId(), currentFingerprint));
		if (Boolean.TRUE.equals(task.getExposureEnabled())) {
			published.add(publishRoute(context, "EXPOSURE", task.getExposureAppLinkId(), currentFingerprint));
		}
		Published primary = published.get(0);
		String bundle = json(published.stream()
			.map(row -> Map.of("routeVersionId", row.routeVersionId(), "monitorId", row.monitorId(), "routeVersion",
					row.routeVersion(), "routeKind", row.routeKind()))
			.toList());
		String responseFingerprint = sha256(request.publishRequestId() + "|" + currentFingerprint + "|" + bundle);
		jdbcTemplate.update("""
				INSERT INTO scx_adx_route_publish_record
				(id,publish_request_id,task_id,monitor_id,expected_task_version,check_fingerprint,route_version_id,
				 route_version,route_bundle_json,status,actor,response_fingerprint,create_time)
				VALUES (?,?,?,?,?,?,?,?,?,'PUBLISHED',?,?,NOW(3))
				""", id(), request.publishRequestId(), taskId, primary.monitorId(), request.expectedTaskVersion(),
				currentFingerprint, primary.routeVersionId(), primary.routeVersion(), bundle, actor(),
				responseFingerprint);
		task.setStatus("READY");
		task.setPublishedTaskVersion(version(task));
		task.setRepublishRequired(false);
		task.setRepublishReason(null);
		task.setUpdateBy(actor());
		taskMapper.updateById(task);
		audit(task, "PUBLISH", version(task), published.size(), "发布事务已持久化不可变版本、事件快照、Outbox 与初始分发；尚未达到实例 ACTIVE 门槛");
		return new RoutePublishViews.PublishResult(request.publishRequestId(), taskId, primary.monitorId(),
				primary.routeVersionId(), primary.routeVersion(), "PUBLISHED", "PENDING", false, primary.publishedAt());
	}

	public RoutePublishViews.PublishResult publishResult(String taskId, String publishRequestId) {
		requireTask(taskId, false, AdxAccessScope.PermissionLevel.VIEW);
		RoutePublishViews.PublishResult result = existingPublish(publishRequestId, taskId);
		if (result == null) {
			throw new IllegalArgumentException("发布请求不存在或无权访问");
		}
		return result;
	}

	public List<RoutePublishViews.VersionItem> versions(String taskId) {
		requireTask(taskId, false, AdxAccessScope.PermissionLevel.VIEW);
		return jdbcTemplate.query("""
				SELECT v.id,v.monitor_id,v.route_version,v.route_kind,v.status,s.acceptance_status,d.status,
				       v.task_config_revision,v.media_credential_secret_revision,v.event_snapshot_schema_version,
				       v.published_at,s.accept_until
				FROM scx_adx_route_version v
				JOIN scx_adx_route_runtime_state s ON s.route_version_id=v.id
				JOIN scx_adx_route_distribution d ON d.route_version_id=v.id AND d.target_group='DEFAULT'
				WHERE v.task_id=? ORDER BY v.published_at DESC,v.route_kind
				""", (rs, rowNum) -> new RoutePublishViews.VersionItem(rs.getString(1), rs.getString(2), rs.getLong(3),
				rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getLong(9),
				rs.getInt(10), rs.getObject(11, LocalDateTime.class), rs.getObject(12, LocalDateTime.class)), taskId);
	}

	public RoutePublishViews.DistributionView distribution(String taskId) {
		requireTask(taskId, false, AdxAccessScope.PermissionLevel.VIEW);
		List<DistributionRow> rows = jdbcTemplate.query("""
				SELECT d.id,d.status,d.minimum_required_acks,
				 SUM(CASE WHEN a.status='APPLIED' THEN 1 ELSE 0 END),
				 SUM(CASE WHEN a.status='FAILED' THEN 1 ELSE 0 END)
				FROM scx_adx_route_distribution d
				JOIN scx_adx_route_version v ON v.id=d.route_version_id
				LEFT JOIN scx_adx_route_instance_ack a ON a.distribution_id=d.id
				WHERE v.task_id=? GROUP BY d.id,d.status,d.minimum_required_acks,d.create_time
				ORDER BY d.create_time DESC
				""", (rs, rowNum) -> new DistributionRow(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
				rs.getInt(5)), taskId);
		List<RoutePublishViews.InstanceView> instances = jdbcTemplate.query("""
				SELECT a.instance_id,a.applied_version,a.status,a.error_code,a.acked_at
				FROM scx_adx_route_instance_ack a JOIN scx_adx_route_version v ON v.id=a.route_version_id
				WHERE v.task_id=? ORDER BY a.acked_at DESC
				""", (rs, rowNum) -> new RoutePublishViews.InstanceView(rs.getString(1), rs.getLong(2), rs.getString(3),
				rs.getString(4), rs.getObject(5, LocalDateTime.class)), taskId);
		String overall = rows.isEmpty() ? "NONE"
				: rows.stream().anyMatch(row -> "FAILED".equals(row.status())) ? "FAILED"
						: rows.stream().anyMatch(row -> "DEGRADED".equals(row.status())) ? "DEGRADED"
								: rows.stream().allMatch(row -> "ACTIVE".equals(row.status())) ? "ACTIVE"
										: rows.stream().anyMatch(row -> "DISTRIBUTING".equals(row.status()))
												? "DISTRIBUTING" : "PENDING";
		return new RoutePublishViews.DistributionView(taskId, overall, "ACTIVE".equals(overall),
				rows.stream().mapToInt(DistributionRow::minimumAcks).max().orElse(properties.getMinimumRequiredAcks()),
				rows.stream().mapToInt(DistributionRow::applied).sum(),
				rows.stream().mapToInt(DistributionRow::failed).sum(), instances);
	}

	@Transactional
	public RoutePublishViews.AddressView copyAddress(String taskId) {
		AdxTask task = requireTask(taskId, true, AdxAccessScope.PermissionLevel.VIEW);
		List<AddressRow> rows = jdbcTemplate.query("""
				SELECT v.id,v.monitor_id,v.route_version,v.public_template_ciphertext,s.accept_until
				FROM scx_adx_route_version v
				JOIN scx_adx_route_runtime_state s ON s.route_version_id=v.id
				JOIN scx_adx_route_distribution d ON d.route_version_id=v.id
				WHERE v.task_id=? AND v.route_kind='CLICK' AND s.acceptance_status IN ('ACTIVE','OVERLAP')
				  AND (s.deny_at IS NULL OR s.deny_at>NOW(3)) AND (s.accept_until IS NULL OR s.accept_until>NOW(3))
				  AND d.status IN ('ACTIVE','DEGRADED')
				ORDER BY v.route_version DESC LIMIT 1
				""", (rs, rowNum) -> new AddressRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4),
				rs.getObject(5, LocalDateTime.class)), taskId);
		if (rows.isEmpty()) {
			throw conflict("ADX_ROUTE_ADDRESS_NOT_ACTIVE：尚无实例证据支持的可复制地址");
		}
		AddressRow row = rows.get(0);
		String auditId = audit(task, "COPY_ROUTE_ADDRESS", version(task), 1,
				"已授权复制当前 ACTIVE 监测地址；审计不记录 routeCode、主机、查询参数或完整 URL");
		return new RoutePublishViews.AddressView(taskId, row.monitorId(), row.routeVersion(),
				protocolCrypto.decrypt(row.templateCiphertext()), row.acceptUntil(), auditId);
	}

	public RoutePublishViews.PausePreview pausePreview(String taskId, RoutePublishRequests.PausePreview request) {
		AdxTask task = requireTask(taskId, false, AdxAccessScope.PermissionLevel.MANAGE);
		if (version(task) != request.expectedTaskVersion()) {
			throw conflict("ADX_TASK_VERSION_CONFLICT：停流预览前任务已变化");
		}
		int active = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM scx_adx_route_runtime_state s JOIN scx_adx_route_version v ON v.id=s.route_version_id
						WHERE v.task_id=? AND s.acceptance_status IN ('ACTIVE','OVERLAP','PENDING_DISTRIBUTION')
						  AND (s.deny_at IS NULL OR s.deny_at>NOW(3))
						""",
				Integer.class, taskId);
		String fingerprint = sha256(taskId + "|" + version(task) + "|" + active + "|PAUSE");
		long expires = Instant.now().plusSeconds(properties.getPublishCheckTtlSeconds()).getEpochSecond();
		String token = protocolCrypto.signToken("ADX_ROUTE_PAUSE_PREVIEW",
				String.join("|", taskId, String.valueOf(version(task)), fingerprint, String.valueOf(expires)));
		return new RoutePublishViews.PausePreview(taskId, version(task), active,
				active == 0 ? "当前无可接收新流量的版本；命令仍保持幂等" : "将写入 deny marker；不删除历史版本或运行事实",
				LocalDateTime.ofInstant(Instant.ofEpochSecond(expires), ZONE), token);
	}

	@Transactional
	public RoutePublishViews.PauseResult pause(String taskId, RoutePublishRequests.Pause request) {
		StpUtil.checkSafe();
		List<RoutePublishViews.PauseResult> existing = jdbcTemplate
			.query("""
					SELECT pause_request_id,task_id,affected_route_count,create_time FROM scx_adx_route_pause_command
					WHERE pause_request_id=?
					""",
					(rs, rowNum) -> new RoutePublishViews.PauseResult(rs.getString(1), rs.getString(2), "PAUSED",
							rs.getInt(3), "DISTRIBUTING", rs.getObject(4, LocalDateTime.class)),
					request.pauseRequestId());
		if (!existing.isEmpty()) {
			if (!taskId.equals(existing.get(0).taskId())) {
				throw conflict("ADX_PAUSE_IDEMPOTENCY_CONFLICT：停流幂等键已用于其他任务");
			}
			requireTask(taskId, false, AdxAccessScope.PermissionLevel.MANAGE);
			return existing.get(0);
		}
		AdxTask task = requireTask(taskId, true, AdxAccessScope.PermissionLevel.MANAGE);
		if (version(task) != request.expectedTaskVersion()) {
			throw conflict("ADX_TASK_VERSION_CONFLICT：停流确认前任务已变化");
		}
		int active = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM scx_adx_route_runtime_state s JOIN scx_adx_route_version v ON v.id=s.route_version_id
						WHERE v.task_id=? AND s.acceptance_status IN ('ACTIVE','OVERLAP','PENDING_DISTRIBUTION')
						  AND (s.deny_at IS NULL OR s.deny_at>NOW(3))
						""",
				Integer.class, taskId);
		String fingerprint = sha256(taskId + "|" + version(task) + "|" + active + "|PAUSE");
		verifyTimedToken("ADX_ROUTE_PAUSE_PREVIEW", request.previewToken(), taskId, request.expectedTaskVersion(),
				fingerprint);
		List<VersionRef> refs = jdbcTemplate.query(
				"""
						SELECT v.id,v.route_version FROM scx_adx_route_version v JOIN scx_adx_route_runtime_state s
						 ON s.route_version_id=v.id WHERE v.task_id=? AND s.acceptance_status IN ('ACTIVE','OVERLAP','PENDING_DISTRIBUTION')
						 AND (s.deny_at IS NULL OR s.deny_at>NOW(3)) FOR UPDATE
						""",
				(rs, rowNum) -> new VersionRef(rs.getString(1), rs.getLong(2)), taskId);
		int denied = jdbcTemplate.update("""
				UPDATE scx_adx_route_runtime_state s JOIN scx_adx_route_version v ON v.id=s.route_version_id
				SET s.acceptance_status='DENIED',s.deny_at=NOW(3),s.state_revision=s.state_revision+1,
				    s.reason_code='TASK_PAUSED',s.update_by=?
				WHERE v.task_id=? AND s.acceptance_status IN ('ACTIVE','OVERLAP','PENDING_DISTRIBUTION')
				  AND (s.deny_at IS NULL OR s.deny_at>NOW(3))
				""", actor(), taskId);
		for (VersionRef ref : refs) {
			enqueue("ROUTE_DENY", ref.id(), ref.version(), "ROUTE_DENIED", ref.id());
		}
		long before = version(task);
		task.setStatus("PAUSED");
		task.setConfigVersion(before + 1);
		task.setRepublishRequired(true);
		task.setRepublishReason("TASK_PAUSED_REQUIRES_NEW_PUBLICATION");
		task.setUpdateBy(actor());
		taskMapper.updateById(task);
		String reason = sanitize(request.reason());
		jdbcTemplate.update("""
				INSERT INTO scx_adx_route_pause_command
				(id,pause_request_id,task_id,expected_task_version,preview_fingerprint,status,affected_route_count,
				 actor,reason_summary,create_time) VALUES (?,?,?,?,?,'DENY_MARKER_WRITTEN',?,?,?,NOW(3))
				""", id(), request.pauseRequestId(), taskId, request.expectedTaskVersion(), fingerprint, denied,
				actor(), reason);
		audit(task, "PAUSE", before, denied, "二次认证停流已写 deny marker；恢复必须重新检查并发布新版本；原因=" + reason);
		return new RoutePublishViews.PauseResult(request.pauseRequestId(), taskId, "PAUSED", denied, "DISTRIBUTING",
				LocalDateTime.now());
	}

	private Published publishRoute(Context context, String routeKind, String appLinkId, String checkFingerprint) {
		AdxMonitorLink monitor = monitor(context, routeKind, appLinkId);
		long nextVersion = jdbcTemplate.queryForObject(
				"SELECT COALESCE(MAX(route_version),0)+1 FROM scx_adx_route_version WHERE monitor_id=?", Long.class,
				monitor.getId());
		String routeCode = trackingPolicy.newRouteCode();
		String routeHash = protocolCrypto.blindIndex("ADX_ROUTE_CODE", routeCode);
		String publicTemplate = trackingPolicy.buildTemplate(routeCode, context.adapter());
		List<EventFrozen> events = context.events();
		String routeVersionId = id();
		String runtimeCiphertext = protocolCrypto.encrypt(
				json(runtimeSnapshot(context, monitor, routeKind, appLinkId, routeVersionId, nextVersion, events)));
		LocalDateTime publishedAt = LocalDateTime.now();
		jdbcTemplate.update("""
				INSERT INTO scx_adx_route_version
				(id,monitor_id,task_id,route_version,route_kind,route_code_hash,route_code_ciphertext,
				 public_template_ciphertext,route_schema_version,snapshot_fingerprint,runtime_snapshot_ciphertext,
				 advertiser_id,advertiser_revision,product_id,product_revision,product_event_policy_revision,
				 app_link_id,app_link_revision,media_account_id,media_account_revision,media_credential_id,
				 media_credential_secret_revision,channel_id,channel_adapter_code,channel_adapter_revision,
				 task_config_revision,event_snapshot_schema_version,macro_schema_json,dedup_window_seconds,
				 exposure_enabled,exposure_as_click,rta_mode,status,published_by,published_at)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PUBLISHED',?,?)
				""", routeVersionId, monitor.getId(), context.task().getId(), nextVersion, routeKind, routeHash,
				protocolCrypto.encrypt(routeCode), protocolCrypto.encrypt(publicTemplate), ROUTE_SCHEMA_VERSION,
				checkFingerprint, runtimeCiphertext, context.advertiser().getId(),
				version(context.advertiser().getConfigVersion()), context.product().getId(),
				version(context.product().getConfigVersion()), version(context.product().getEventPolicyRevision()),
				appLinkId, version(link(context, appLinkId).getConfigVersion()), context.account().getId(),
				version(context.account().getConfigVersion()), context.credential().getId(),
				version(context.credential().getCurrentSecretRevision()), context.channel().getId(),
				context.channel().getAdapterCode(), context.adapterRevision(), version(context.task()),
				EVENT_SNAPSHOT_SCHEMA_VERSION, context.tracking().macroSchemaJson(),
				context.task().getDedupWindowSeconds(), Boolean.TRUE.equals(context.task().getExposureEnabled()),
				Boolean.TRUE.equals(context.task().getExposureAsClick()), "OFF", actor(), publishedAt);
		persistEventSnapshots(context, monitor.getId(), nextVersion);
		jdbcTemplate.update(
				"""
						INSERT INTO scx_adx_route_runtime_state
						(id,route_version_id,monitor_id,route_version,acceptance_status,state_revision,reason_code,update_by,
						 create_time,update_time) VALUES (?,?,?,?,'PENDING_DISTRIBUTION',1,'AWAITING_INSTANCE_ACK',?,NOW(3),NOW(3))
						""",
				id(), routeVersionId, monitor.getId(), nextVersion, actor());
		String distributionId = id();
		jdbcTemplate.update("""
				INSERT INTO scx_adx_route_distribution
				(id,route_version_id,monitor_id,route_version,target_group,minimum_required_acks,status,attempt_count,
				 next_attempt_at,create_time,update_time)
				VALUES (?,?,?,?,'DEFAULT',?,'PENDING',0,NOW(3),NOW(3),NOW(3))
				""", distributionId, routeVersionId, monitor.getId(), nextVersion, properties.getMinimumRequiredAcks());
		enqueue("ROUTE_VERSION", routeVersionId, nextVersion, "ROUTE_PUBLISHED", distributionId);
		jdbcTemplate.update(
				"""
						UPDATE scx_adx_monitor_link SET route_status='PUBLISHED',config_version=?,published_at=NOW(3),
						 republish_required=0,republish_reason=NULL,task_config_revision=?,event_snapshot_version=2,
						 event_mapping_mode='SNAPSHOT',published_product_id=?,published_product_version=?,
						 published_product_event_policy_revision=?,published_app_link_id=?,published_app_link_version=?,
						 published_media_account_id=?,published_media_account_version=?,published_media_account_credential_revision=?,
						 published_media_credential_id=?,published_media_credential_secret_revision=? WHERE id=?
						""",
				nextVersion, version(context.task()), context.product().getId(),
				version(context.product().getConfigVersion()), version(context.product().getEventPolicyRevision()),
				appLinkId, version(link(context, appLinkId).getConfigVersion()), context.account().getId(),
				version(context.account().getConfigVersion()), version(context.account().getCredentialRevision()),
				context.credential().getId(), version(context.credential().getCurrentSecretRevision()),
				monitor.getId());
		return new Published(routeVersionId, monitor.getId(), nextVersion, routeKind, publishedAt);
	}

	private AdxMonitorLink monitor(Context context, String routeKind, String appLinkId) {
		AdxMonitorLink existing = monitorMapper.selectOne(Wrappers.<AdxMonitorLink>lambdaQuery()
			.eq(AdxMonitorLink::getTaskId, context.task().getId())
			.eq(AdxMonitorLink::getTaskRouteKind, routeKind)
			.eq(AdxMonitorLink::getDeleted, false)
			.last("FOR UPDATE"));
		if (existing != null) {
			if (!appLinkId.equals(existing.getAppLinkId()) || !context.channel().getId().equals(existing.getChannelId())
					|| !context.account().getId().equals(existing.getMediaAccountId())) {
				throw validation("ADX_MONITOR_RELATION_STALE：Monitor 与当前 Task 关系不一致");
			}
			return existing;
		}
		AdxMonitorLink monitor = new AdxMonitorLink();
		monitor.setTaskId(context.task().getId());
		monitor.setTaskMappingStatus("READY");
		monitor.setTaskRouteKind(routeKind);
		monitor.setTaskConfigRevision(version(context.task()));
		monitor.setChannelId(context.channel().getId());
		monitor.setMediaAccountId(context.account().getId());
		monitor.setMediaCredentialId(context.credential().getId());
		monitor.setAdvertiserId(context.advertiser().getId());
		monitor.setAppLinkId(appLinkId);
		monitor.setMonitorName(
				(context.task().getTaskName() + " · " + routeKind + " · " + context.task().getBizCode()).substring(0,
						Math.min(100,
								(context.task().getTaskName() + " · " + routeKind + " · " + context.task().getBizCode())
									.length())));
		monitor.setBizId(context.task().getBizCode() + "_" + routeKind);
		monitor.setUnitPrice(BigDecimal.ZERO);
		monitor.setRouteStatus("DRAFT");
		monitor.setConfigVersion(1L);
		monitor.setRtaMode("OFF");
		monitor.setBillingMode("OFF");
		monitor.setClickDedupWindowSeconds(context.task().getDedupWindowSeconds());
		monitor.setEventSnapshotVersion(0);
		monitor.setEventMappingMode("LEGACY_UNMAPPED");
		monitor.setRepublishRequired(true);
		monitor.setRepublishReason("INITIAL_TASK_ROUTE_PUBLICATION");
		monitorMapper.insert(monitor);
		return monitor;
	}

	private Context context(AdxTask task) {
		AdxAdvertiser advertiser = required(advertiserMapper.selectById(task.getAdvertiserId()), "广告主不存在或无权访问");
		AdxProduct product = required(productMapper.selectById(task.getProductId()), "Product 不存在或无权访问");
		AdxAppLink click = required(appLinkMapper.selectById(task.getClickAppLinkId()), "点击 AppLink 不存在或无权访问");
		AdxAppLink exposure = StringUtils.hasText(task.getExposureAppLinkId())
				? required(appLinkMapper.selectById(task.getExposureAppLinkId()), "曝光 AppLink 不存在或无权访问") : null;
		AdxChannel channel = required(channelMapper.selectById(task.getChannelId()), "Channel 不存在或无权访问");
		AdxMediaAccount account = required(mediaAccountMapper.selectById(task.getMediaAccountId()),
				"MediaAccount 不存在或无权访问");
		AdxMediaCredential credential = required(mediaCredentialMapper.selectById(task.getMediaCredentialId()),
				"MediaCredential 不存在或无权访问");
		AdxAdapterService.PublishedAdapter adapter = adapterService.requireEnabled(channel.getAdapterCode(),
				"DOWNSTREAM");
		long adapterRevision = jdbcTemplate.queryForObject(
				"SELECT capability_revision FROM scx_ocpx_adapter WHERE adapter_code=? AND direction='DOWNSTREAM' AND status='ACTIVE'",
				Long.class, channel.getAdapterCode());
		List<EventFrozen> events = jdbcTemplate.query(
				"""
						SELECT b.id,b.config_version,e.id,e.config_version,e.event_code,m.id,m.config_version,m.external_code,
						       m.parameter_template_code,b.callback_ratio_basis_points,b.limit_value,b.limit_window,
						       b.limit_timezone,b.over_limit_delivery,b.sampling_algorithm,
						       SHA2(CONCAT_WS('|',b.task_id,t.config_version,b.id,b.config_version,e.id,e.config_version,
						       m.id,m.config_version,e.event_code,m.external_code,b.callback_ratio_basis_points,
						       IFNULL(b.limit_value,'UNLIMITED'),IF(b.over_limit_delivery=1,'true','false'),b.sampling_algorithm),256)
						FROM scx_adx_task_event_binding b JOIN scx_adx_task t ON t.id=b.task_id
						JOIN scx_adx_event_definition e ON e.id=b.event_definition_id
						JOIN scx_adx_channel_event_mapping m ON m.id=b.channel_event_mapping_id
						WHERE b.task_id=? AND b.status='ACTIVE' AND e.status='ACTIVE' AND e.mapping_state='CANONICAL'
						 AND m.status='ACTIVE' AND m.mapping_state='CANONICAL' AND m.direction='DOWNSTREAM_DELIVERY'
						ORDER BY e.event_code
						""",
				(rs, rowNum) -> new EventFrozen(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getLong(4),
						rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8), rs.getString(9),
						rs.getInt(10), rs.getObject(11, Long.class), rs.getString(12), rs.getString(13),
						rs.getBoolean(14), rs.getString(15), rs.getString(16)),
				task.getId());
		return new Context(task, advertiser, product, click, exposure, channel, account, credential, adapter,
				adapterRevision, events, trackingPolicy.assess(adapter));
	}

	private List<RoutePublishViews.CheckItem> checks(Context context) {
		List<RoutePublishViews.CheckItem> checks = new ArrayList<>();
		boolean active = "ACTIVE".equals(context.advertiser().getStatus())
				&& "ACTIVE".equals(context.product().getStatus()) && "ACTIVE".equals(context.click().getStatus())
				&& "READY".equals(context.click().getMappingStatus()) && "ACTIVE".equals(context.channel().getStatus())
				&& "ACTIVE".equals(context.account().getStatus()) && "ACTIVE".equals(context.credential().getStatus());
		checks.add(new RoutePublishViews.CheckItem("DEPENDENCIES_ACTIVE", active ? "PASS" : "FAIL",
				active ? "主数据依赖均处于可发布状态" : "存在停用、归档或待补映射的依赖"));
		boolean relations = context.advertiser().getId().equals(context.product().getAdvertiserId())
				&& context.product().getId().equals(context.click().getProductId())
				&& context.advertiser().getId().equals(context.click().getAdvertiserId())
				&& context.channel().getId().equals(context.account().getChannelId())
				&& context.account().getId().equals(context.credential().getMediaAccountId())
				&& (!Boolean.TRUE.equals(context.task().getExposureEnabled()) || context.exposure() != null
						&& context.product().getId().equals(context.exposure().getProductId()));
		checks.add(new RoutePublishViews.CheckItem("RELATION_SCOPE", relations ? "PASS" : "FAIL",
				relations ? "Advertiser、Product、AppLink、Channel 与 MediaAccount 关系一致" : "对象关系不一致"));
		boolean revision = version(context.credential().getCurrentSecretRevision()) == version(
				context.task().getMediaCredentialSecretRevision());
		checks.add(new RoutePublishViews.CheckItem("CREDENTIAL_REVISION", revision ? "PASS" : "FAIL",
				revision ? "媒体凭据 revision 与 Task 草稿一致" : "媒体凭据已轮换，请重新保存 Task"));
		checks.add(new RoutePublishViews.CheckItem("EVENT_SNAPSHOT", context.events().isEmpty() ? "FAIL" : "PASS",
				context.events().isEmpty() ? "Task 尚无可冻结的 canonical 事件绑定" : "canonical 事件绑定可冻结为 schema 2"));
		checks
			.add(new RoutePublishViews.CheckItem("RTA_OFF", "OFF".equals(context.task().getRtaMode()) ? "PASS" : "FAIL",
					"OFF".equals(context.task().getRtaMode()) ? "RTA 固定为 OFF" : "非 OFF RTA 被发布器拒绝"));
		checks.add(new RoutePublishViews.CheckItem("TASK_STATE",
				List.of("READY", "PAUSED").contains(context.task().getStatus()) ? "PASS" : "FAIL",
				List.of("READY", "PAUSED").contains(context.task().getStatus()) ? "Task 可进入独立发布动作"
						: "Task 必须先达到 READY；归档或草稿不可发布"));
		context.tracking()
			.checks()
			.forEach(item -> checks.add(new RoutePublishViews.CheckItem(item.code(), item.status(), item.message())));
		return List.copyOf(checks);
	}

	private RoutePublishViews.ConfigurationSummary summary(Context context) {
		return new RoutePublishViews.ConfigurationSummary(version(context.advertiser().getConfigVersion()),
				version(context.product().getConfigVersion()), version(context.product().getEventPolicyRevision()),
				version(context.click().getConfigVersion()), version(context.account().getConfigVersion()),
				version(context.credential().getCurrentSecretRevision()), context.adapterRevision(),
				context.events().size(), context.task().getDedupWindowSeconds(),
				Boolean.TRUE.equals(context.task().getExposureEnabled()),
				Boolean.TRUE.equals(context.task().getExposureAsClick()), "OFF", ROUTE_SCHEMA_VERSION,
				EVENT_SNAPSHOT_SCHEMA_VERSION);
	}

	private String fingerprint(Context context) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("taskId", context.task().getId());
		values.put("taskRevision", version(context.task()));
		values.put("advertiserRevision", version(context.advertiser().getConfigVersion()));
		values.put("productRevision", version(context.product().getConfigVersion()));
		values.put("productEventRevision", version(context.product().getEventPolicyRevision()));
		values.put("clickLinkRevision", version(context.click().getConfigVersion()));
		values.put("exposureLinkRevision",
				context.exposure() == null ? 0 : version(context.exposure().getConfigVersion()));
		values.put("mediaAccountRevision", version(context.account().getConfigVersion()));
		values.put("mediaCredentialRevision", version(context.credential().getCurrentSecretRevision()));
		values.put("adapterRevision", context.adapterRevision());
		values.put("macroFingerprint", context.tracking().templateFingerprint());
		values.put("eventFingerprints", context.events().stream().map(EventFrozen::fingerprint).toList());
		values.put("dedup", context.task().getDedupWindowSeconds());
		values.put("exposure", context.task().getExposureEnabled());
		values.put("exposureAsClick", context.task().getExposureAsClick());
		values.put("rta", context.task().getRtaMode());
		return sha256(json(values));
	}

	private RuntimeFrozenSnapshot runtimeSnapshot(Context context, AdxMonitorLink monitor, String routeKind,
			String appLinkId, String routeVersionId, long routeVersion, List<EventFrozen> events) {
		String upstreamSecret = resolveAdvertiserSecret(context.advertiser());
		List<RouteEventSnapshot> compatibleEvents = events.stream()
			.map(event -> new RouteEventSnapshot(event.mappingId(), event.standardCode(), event.standardCode(),
					event.standardCode(), event.standardCode(), event.externalCode(), event.externalCode(),
					BigDecimal.ZERO,
					event.limitValue() == null ? 0 : Math.toIntExact(Math.min(Integer.MAX_VALUE, event.limitValue()))))
			.toList();
		RouteSnapshot route = new RouteSnapshot(monitor.getId(), monitor.getBizId(), routeVersion,
				context.channel().getId(), context.channel().getChannelCode(), context.channel().getAdapterCode(),
				context.channel().getAuthType(), context.channel().getKeyVersion(), context.channel().getSecretKey(),
				context.advertiser().getId(), appLinkId, context.advertiser().getAdapterCode(), upstreamSecret,
				context.advertiser().getBidUrl(), context.advertiser().getMacroParams(), null, BigDecimal.ZERO,
				BigDecimal.ONE, "OFF", "OFF", context.task().getDedupWindowSeconds(), compatibleEvents,
				EVENT_SNAPSHOT_SCHEMA_VERSION, "SNAPSHOT")
			.withFactDimensions(SINGLE_ENTERPRISE_TENANT, context.product().getId(), context.task().getId(),
					context.account().getId(), context.task().getOperatorUserId(), routeVersionId);
		return new RuntimeFrozenSnapshot(ROUTE_SCHEMA_VERSION, routeKind, route, events, context.task().getId(),
				version(context.task()), context.product().getId(), version(context.product().getConfigVersion()),
				context.account().getId(), version(context.account().getConfigVersion()), context.credential().getId(),
				version(context.credential().getCurrentSecretRevision()), context.tracking().macroSchemaJson(),
				link(context, appLinkId).getLinkUrlTemplate(), link(context, appLinkId).getDeeplinkUrl(),
				link(context, appLinkId).getDownloadUrl());
	}

	private String resolveAdvertiserSecret(AdxAdvertiser advertiser) {
		List<SecretRevision> revisions = jdbcTemplate.query("""
				SELECT secret_ciphertext,storage_mode,status,overlap_until FROM scx_adx_advertiser_credential_revision
				WHERE advertiser_id=? AND credential_revision=?
				""",
				(rs, rowNum) -> new SecretRevision(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getObject(4, LocalDateTime.class)),
				advertiser.getId(), version(advertiser.getCredentialRevision()));
		if (revisions.isEmpty() || !"ENCRYPTED".equals(revisions.get(0).storageMode())
				|| !List.of("ACTIVE", "OVERLAP").contains(revisions.get(0).status())
				|| "OVERLAP".equals(revisions.get(0).status()) && (revisions.get(0).overlapUntil() == null
						|| !revisions.get(0).overlapUntil().isAfter(LocalDateTime.now()))) {
			throw validation("ADX_ADVERTISER_CREDENTIAL_UNAVAILABLE：发布只接受当前有效的加密凭据 revision");
		}
		return protocolCrypto.decrypt(revisions.get(0).ciphertext());
	}

	private void persistEventSnapshots(Context context, String monitorId, long routeVersion) {
		for (EventFrozen event : context.events()) {
			jdbcTemplate.update("""
					INSERT INTO scx_adx_route_event_snapshot
					(id,monitor_id,route_version,snapshot_schema_version,snapshot_mode,task_id,task_config_revision,
					 task_event_binding_id,task_event_binding_revision,event_definition_id,event_definition_revision,
					 event_mapping_id,event_mapping_revision,standard_event_code,direction,external_event_code,
					 parameter_template_code,callback_ratio_basis_points,limit_value,limit_window,limit_timezone,
					 over_limit_delivery,sampling_algorithm,snapshot_fingerprint,create_time)
					VALUES (?,?,?,2,'CANONICAL',?,?,?,?,?,?,?,?,?,'DOWNSTREAM_DELIVERY',?,?,?,?,?,?,?,?,?,NOW(3))
					""", id(), monitorId, routeVersion, context.task().getId(), version(context.task()),
					event.bindingId(), event.bindingRevision(), event.definitionId(), event.definitionRevision(),
					event.mappingId(), event.mappingRevision(), event.standardCode(), event.externalCode(),
					event.parameterTemplate(), event.ratio(), event.limitValue(), event.limitWindow(),
					event.limitTimezone(), event.overLimit(), event.algorithm(), event.fingerprint());
		}
	}

	private void enqueue(String aggregateType, String aggregateId, long version, String eventType, String referenceId) {
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_config_outbox
				(id,aggregate_type,aggregate_id,config_version,event_type,payload_json,published,delivery_status,
				 attempt_count,available_at,create_time,update_time)
				VALUES (?,?,?,?,?,JSON_OBJECT('referenceId',?),0,'PENDING',0,NOW(3),NOW(3),NOW(3))
				""", id(), aggregateType, aggregateId, version, eventType, referenceId);
	}

	private RoutePublishViews.PublishResult existingPublish(String requestId, String taskId) {
		List<RoutePublishViews.PublishResult> rows = jdbcTemplate.query("""
				SELECT p.publish_request_id,p.task_id,p.monitor_id,p.route_version_id,p.route_version,p.status,
				 d.status,v.published_at FROM scx_adx_route_publish_record p
				JOIN scx_adx_route_version v ON v.id=p.route_version_id
				JOIN scx_adx_route_distribution d ON d.route_version_id=v.id AND d.target_group='DEFAULT'
				WHERE p.publish_request_id=?
				""",
				(rs, rowNum) -> new RoutePublishViews.PublishResult(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7),
						"ACTIVE".equals(rs.getString(7)), rs.getObject(8, LocalDateTime.class)),
				requestId);
		if (rows.isEmpty()) {
			return null;
		}
		if (!taskId.equals(rows.get(0).taskId())) {
			throw conflict("ADX_PUBLISH_IDEMPOTENCY_CONFLICT：publishRequestId 已用于其他任务");
		}
		requireTask(taskId, false, AdxAccessScope.PermissionLevel.VIEW);
		return rows.get(0);
	}

	private void verifyCheckToken(String token, String taskId, long version, String fingerprint) {
		verifyTimedToken("ADX_ROUTE_PUBLISH_CHECK", token, taskId, version, fingerprint);
	}

	private void verifyTimedToken(String purpose, String token, String taskId, long version, String fingerprint) {
		String[] values = protocolCrypto.verifyToken(purpose, token).split("\\|", -1);
		if (values.length != 4 || !taskId.equals(values[0]) || !String.valueOf(version).equals(values[1])
				|| !fingerprint.equals(values[2])) {
			throw conflict("ADX_CHECK_STALE：检查结果与当前 Task 或依赖 revision 不一致");
		}
		try {
			if (Instant.now().getEpochSecond() > Long.parseLong(values[3])) {
				throw conflict("ADX_CHECK_EXPIRED：检查结果已过期，请重新校验");
			}
		}
		catch (NumberFormatException error) {
			throw conflict("ADX_CHECK_INVALID：检查 token 无效");
		}
	}

	private AdxTask requireTask(String taskId, boolean lock, AdxAccessScope.PermissionLevel level) {
		AdxTask task = lock
				? taskMapper.selectOne(Wrappers.<AdxTask>lambdaQuery().eq(AdxTask::getId, taskId).last("FOR UPDATE"))
				: taskMapper.selectById(taskId);
		if (task == null) {
			throw new IllegalArgumentException("任务不存在或无权访问");
		}
		accessScope.require(AdxAccessScope.ScopeType.ADVERTISER, task.getAdvertiserId(), level);
		accessScope.require(AdxAccessScope.ScopeType.CHANNEL, task.getChannelId(), level);
		accessScope.require(AdxAccessScope.ScopeType.MEDIA_ACCOUNT, task.getMediaAccountId(), level);
		return task;
	}

	private AdxAppLink link(Context context, String id) {
		if (context.click().getId().equals(id)) {
			return context.click();
		}
		if (context.exposure() != null && context.exposure().getId().equals(id)) {
			return context.exposure();
		}
		throw new IllegalArgumentException("Task AppLink 关系无效");
	}

	private String overall(List<RoutePublishViews.CheckItem> items) {
		return items.stream().anyMatch(item -> "FAIL".equals(item.status())) ? "FAIL"
				: items.stream().anyMatch(item -> "WARN".equals(item.status())) ? "WARN" : "PASS";
	}

	private String audit(AdxTask task, String action, long before, int affected, String summary) {
		String auditId = id();
		jdbcTemplate.update("""
				INSERT INTO scx_adx_task_change_audit
				(id,task_id,advertiser_id,channel_id,media_account_id,action,before_config_version,
				 after_config_version,affected_monitor_count,actor,change_summary,create_time)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW(3))
				""", auditId, task.getId(), task.getAdvertiserId(), task.getChannelId(), task.getMediaAccountId(),
				action, before, version(task), affected, actor(),
				summary.length() > 500 ? summary.substring(0, 500) : summary);
		return auditId;
	}

	private String sanitize(String value) {
		String sanitized = value.trim()
			.replaceAll("[\\r\\n\\t]+", " ")
			.replaceAll("(?i)(https?://|secret|password|token|api[-_]?key|credential)[^\\s]*", "[REDACTED]")
			.replaceAll("[A-Za-z0-9+/=_-]{32,}", "[REDACTED]");
		return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (Exception error) {
			throw new IllegalStateException("无法构建不可变发布快照", error);
		}
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法计算发布摘要", error);
		}
	}

	private String actor() {
		String actor = SecurityUtils.getUserName();
		return StringUtils.hasText(actor) ? actor : "unknown";
	}

	private long version(AdxTask task) {
		return version(task.getConfigVersion());
	}

	private long version(Long value) {
		return value == null ? 0 : value;
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private <T> T required(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

	private BootBusinessException validation(String message) {
		return new BootBusinessException(HttpStatus.UNPROCESSABLE_ENTITY.value(), message);
	}

	private record Context(AdxTask task, AdxAdvertiser advertiser, AdxProduct product, AdxAppLink click,
			AdxAppLink exposure, AdxChannel channel, AdxMediaAccount account, AdxMediaCredential credential,
			AdxAdapterService.PublishedAdapter adapter, long adapterRevision, List<EventFrozen> events,
			TrackingRoutePolicy.Assessment tracking) {
	}

	private record EventFrozen(String bindingId, long bindingRevision, String definitionId, long definitionRevision,
			String standardCode, String mappingId, long mappingRevision, String externalCode, String parameterTemplate,
			int ratio, Long limitValue, String limitWindow, String limitTimezone, boolean overLimit, String algorithm,
			String fingerprint) {
	}

	private record RuntimeFrozenSnapshot(int routeSchemaVersion, String routeKind, RouteSnapshot route,
			List<EventFrozen> canonicalEvents, String taskId, long taskRevision, String productId, long productRevision,
			String mediaAccountId, long mediaAccountRevision, String mediaCredentialId,
			long mediaCredentialSecretRevision, String macroSchemaJson, String linkUrlTemplate, String deeplinkUrl,
			String downloadUrl) {
	}

	private record Published(String routeVersionId, String monitorId, long routeVersion, String routeKind,
			LocalDateTime publishedAt) {
	}

	private record DistributionRow(String id, String status, int minimumAcks, int applied, int failed) {
	}

	private record AddressRow(String routeVersionId, String monitorId, long routeVersion, String templateCiphertext,
			LocalDateTime acceptUntil) {
	}

	private record VersionRef(String id, long version) {
	}

	private record SecretRevision(String ciphertext, String storageMode, String status, LocalDateTime overlapUntil) {
	}

}
