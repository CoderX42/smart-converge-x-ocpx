package com.yzytech.scx.ocpx.runtime.config;

import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.contract.RouteEventSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class RouteConfigurationRepository {

	private static final String ROUTE_SELECT = """
			SELECT m.id, m.biz_id, m.config_version, c.id, c.channel_code, c.adapter_code, c.auth_type,
			      c.key_version, c.secret_key, a.id, m.app_link_id, a.adapter_code, a.secret_key,
			      m.published_upstream_feedback_url,
			      a.macro_params, m.downstream_callback_url, m.unit_price, COALESCE(d.ratio, 1.0000), m.rta_mode,
			      m.billing_mode, m.click_dedup_window_seconds, COALESCE(m.event_snapshot_version,0)
			FROM scx_adx_monitor_link m
			JOIN scx_adx_channel c ON c.id=m.channel_id AND c.deleted=0 AND c.status='ACTIVE'
			JOIN scx_adx_advertiser a ON a.id=m.advertiser_id AND a.deleted=0 AND a.status='ACTIVE'
			LEFT JOIN scx_adx_deduction_rule d ON d.channel_id=m.channel_id AND d.app_link_id=m.app_link_id AND d.deleted=0
			""";

	private static final String PUBLISHED = " m.deleted=0 AND m.route_status='PUBLISHED' ";

	private static final org.springframework.jdbc.core.RowMapper<PublishedRoute> ROW_MAPPER = (rs,
			rowNum) -> new PublishedRoute(new RouteSnapshot(rs.getString(1), rs.getString(2), rs.getLong(3),
					rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8), rs.getString(9),
					rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
					rs.getString(15), rs.getString(16), rs.getObject(17, BigDecimal.class),
					rs.getObject(18, BigDecimal.class), rs.getString(19), rs.getString(20), rs.getInt(21), List.of(),
					rs.getInt(22)),
			rs.getString(2));

	private final JdbcTemplate jdbcTemplate;

	public RouteConfigurationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<RouteSnapshot> findPublished(String channelCode, String routeReference) {
		List<PublishedRoute> rows = jdbcTemplate.query(
				ROUTE_SELECT + " WHERE c.channel_code=? AND (m.id=? OR m.biz_id=?) AND " + PUBLISHED + " LIMIT 1",
				ROW_MAPPER, channelCode, routeReference, routeReference);
		return rows.stream().map(PublishedRoute::route).findFirst().map(this::attachEventMappings);
	}

	public List<PublishedRoute> findAllPublished() {
		return attachEventMappings(jdbcTemplate.query(ROUTE_SELECT + " WHERE " + PUBLISHED, ROW_MAPPER));
	}

	public Optional<RouteSnapshot> findPublishedById(String monitorId) {
		List<PublishedRoute> rows = jdbcTemplate.query(ROUTE_SELECT + " WHERE m.id=? AND " + PUBLISHED + " LIMIT 1",
				ROW_MAPPER, monitorId);
		return rows.stream().map(PublishedRoute::route).findFirst().map(this::attachEventMappings);
	}

	private RouteSnapshot attachEventMappings(RouteSnapshot route) {
		return route.withEventMappings(jdbcTemplate.query("""
				SELECT event_mapping_id,upstream_event_code,upstream_event_name,conversion_event_code,
				 conversion_event_name,media_event_code,media_event_name,assessment_unit_price,daily_limit
				FROM scx_adx_route_event_snapshot WHERE monitor_id=? AND route_version=?
				ORDER BY upstream_event_code
				""", EVENT_MAPPER, route.routeId(), route.routeVersion()));
	}

	private List<PublishedRoute> attachEventMappings(List<PublishedRoute> routes) {
		if (routes.isEmpty()) {
			return routes;
		}
		String predicates = String.join(" OR ", Collections.nCopies(routes.size(), "(monitor_id=? AND route_version=?)"));
		List<Object> parameters = new ArrayList<>();
		routes.forEach(row -> {
			parameters.add(row.route().routeId());
			parameters.add(row.route().routeVersion());
		});
		Map<String, List<RouteEventSnapshot>> events = new HashMap<>();
		jdbcTemplate.query("""
				SELECT monitor_id,route_version,event_mapping_id,upstream_event_code,upstream_event_name,
				 conversion_event_code,conversion_event_name,media_event_code,media_event_name,
				 assessment_unit_price,daily_limit
				FROM scx_adx_route_event_snapshot WHERE """ + predicates, rs -> {
			String key = rs.getString(1) + "|" + rs.getLong(2);
			events.computeIfAbsent(key, ignored -> new ArrayList<>())
				.add(new RouteEventSnapshot(rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
						rs.getString(7), rs.getString(8), rs.getString(9), rs.getBigDecimal(10), rs.getInt(11)));
		}, parameters.toArray());
		return routes.stream()
			.map(row -> new PublishedRoute(row.route().withEventMappings(
					events.getOrDefault(row.route().routeId() + "|" + row.route().routeVersion(), List.of())), row.bizId()))
			.toList();
	}

	private static final org.springframework.jdbc.core.RowMapper<RouteEventSnapshot> EVENT_MAPPER = (rs,
			rowNum) -> new RouteEventSnapshot(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getInt(9));

	public record PublishedRoute(RouteSnapshot route, String bizId) {
	}

}
