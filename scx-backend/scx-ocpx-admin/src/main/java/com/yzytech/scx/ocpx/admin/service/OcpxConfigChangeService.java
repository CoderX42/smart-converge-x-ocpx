package com.yzytech.scx.ocpx.admin.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OcpxConfigChangeService {

	private final JdbcTemplate jdbcTemplate;

	public OcpxConfigChangeService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void channelChanged(String channelId) {
		draftRoutes("channel_id=?", channelId);
	}

	public int advertiserChanged(String advertiserId) {
		return advertiserChanged(advertiserId, "广告主接入配置已变更");
	}

	public int advertiserChanged(String advertiserId, String reason) {
		return jdbcTemplate.update("""
				UPDATE scx_ocpx_monitor_link
				SET republish_required=1,republish_reason=?
				WHERE deleted=0 AND route_status='PUBLISHED' AND advertiser_id=?
				""", reason, advertiserId);
	}

	public int appLinkChanged(String appLinkId) {
		return appLinkChanged(appLinkId, "应用链接配置已变更");
	}

	public int appLinkChanged(String appLinkId, String reason) {
		return jdbcTemplate.update("""
				UPDATE scx_ocpx_monitor_link
				SET republish_required=1,republish_reason=?
				WHERE deleted=0 AND route_status='PUBLISHED' AND app_link_id=?
				""", reason, appLinkId);
	}

	public int productChanged(String productId, String reason) {
		return jdbcTemplate.update("""
				UPDATE scx_ocpx_monitor_link m
				JOIN scx_ocpx_app_link l ON l.id=m.app_link_id AND l.deleted=0
				SET m.republish_required=1,m.republish_reason=?
				WHERE m.deleted=0 AND m.route_status='PUBLISHED' AND l.product_id=?
				""", reason, productId);
	}

	public int mediaAccountChanged(String mediaAccountId, String reason) {
		return jdbcTemplate.update("""
				UPDATE scx_ocpx_monitor_link
				SET republish_required=1,republish_reason=?
				WHERE deleted=0 AND route_status='PUBLISHED' AND media_account_id=?
				""", reason, mediaAccountId);
	}

	/**
	 * Task edits only mark already-published dependent Monitor rows for a future explicit
	 * publish. They never enqueue Outbox work or alter frozen snapshots.
	 */
	public int taskChanged(String taskId, String reason) {
		return jdbcTemplate.update("""
				UPDATE scx_ocpx_monitor_link
				SET republish_required=1,republish_reason=?
				WHERE deleted=0 AND route_status='PUBLISHED' AND task_id=?
				""", reason, taskId);
	}

	public void deductionChanged(String channelId, String appLinkId) {
		draftRoutes("channel_id=? AND app_link_id=?", channelId, appLinkId);
	}

	public void enqueueMonitor(String monitorId, long configVersion, String eventType) {
		jdbcTemplate.update("""
				INSERT IGNORE INTO scx_ocpx_config_outbox
				(id,aggregate_type,aggregate_id,config_version,event_type,published,create_time)
				VALUES (?,'ROUTE',?,?,?,0,NOW(3))
				""", id(), monitorId, configVersion, eventType);
	}

	private void draftRoutes(String predicate, Object... args) {
		List<RouteVersion> routes = jdbcTemplate.query(
				"SELECT id,config_version FROM scx_ocpx_monitor_link WHERE deleted=0 AND " + predicate,
				(rs, rowNum) -> new RouteVersion(rs.getString(1), rs.getLong(2)), args);
		jdbcTemplate.update("UPDATE scx_ocpx_monitor_link SET route_status='DRAFT',published_at=NULL,"
				+ "published_upstream_feedback_url=NULL," + "config_version=config_version+1 WHERE deleted=0 AND "
				+ predicate, args);
		for (RouteVersion route : routes) {
			enqueueMonitor(route.id(), route.version() + 1, "ROUTE_DRAFTED");
		}
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private record RouteVersion(String id, long version) {
	}

}
