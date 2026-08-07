package com.yzytech.scx.adx.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AdxConfigChangeService {

	private final JdbcTemplate jdbcTemplate;

	public AdxConfigChangeService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void channelChanged(String channelId) {
		draftRoutes("channel_id=?", channelId);
	}

	public void advertiserChanged(String advertiserId) {
		draftRoutes("advertiser_id=?", advertiserId);
	}

	public void appLinkChanged(String appLinkId) {
		draftRoutes("app_link_id=?", appLinkId);
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
				"SELECT id,config_version FROM scx_adx_monitor_link WHERE deleted=0 AND " + predicate,
				(rs, rowNum) -> new RouteVersion(rs.getString(1), rs.getLong(2)), args);
		jdbcTemplate.update("UPDATE scx_adx_monitor_link SET route_status='DRAFT',published_at=NULL,"
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
