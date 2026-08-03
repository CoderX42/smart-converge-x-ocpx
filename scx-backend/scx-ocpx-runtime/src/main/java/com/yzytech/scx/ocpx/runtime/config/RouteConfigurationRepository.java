package com.yzytech.scx.ocpx.runtime.config;

import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class RouteConfigurationRepository {

	private static final String ROUTE_SELECT = """
			SELECT m.id, m.biz_id, m.config_version, c.id, c.channel_code, c.adapter_code, c.auth_type,
			      c.key_version, c.secret_key, a.id, m.app_link_id, a.adapter_code, a.secret_key, a.bid_url,
			      a.macro_params, m.downstream_callback_url, COALESCE(d.ratio, 1.0000), m.rta_mode, m.billing_mode
			FROM scx_adx_monitor_link m
			JOIN scx_adx_channel c ON c.id=m.channel_id AND c.deleted=0 AND c.status='ACTIVE'
			JOIN scx_adx_advertiser a ON a.id=m.advertiser_id AND a.deleted=0 AND a.status='ACTIVE'
			LEFT JOIN scx_adx_deduction_rule d ON d.channel_id=m.channel_id AND d.app_link_id=m.app_link_id AND d.deleted=0
			""";

	private static final String PUBLISHED = " m.deleted=0 AND m.route_status='PUBLISHED' ";

	private static final org.springframework.jdbc.core.RowMapper<PublishedRoute> ROW_MAPPER = (rs,
			rowNum) -> new PublishedRoute(new RouteSnapshot(rs.getString(1), rs.getLong(3), rs.getString(4),
					rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8), rs.getString(9), rs.getString(10),
					rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15),
					rs.getString(16), rs.getObject(17, BigDecimal.class), rs.getString(18), rs.getString(19)),
					rs.getString(2));

	private final JdbcTemplate jdbcTemplate;

	public RouteConfigurationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<RouteSnapshot> findPublished(String channelCode, String routeReference) {
		List<PublishedRoute> rows = jdbcTemplate.query(
				ROUTE_SELECT + " WHERE c.channel_code=? AND (m.id=? OR m.biz_id=?) AND " + PUBLISHED + " LIMIT 1",
				ROW_MAPPER, channelCode, routeReference, routeReference);
		return rows.stream().map(PublishedRoute::route).findFirst();
	}

	public List<PublishedRoute> findAllPublished() {
		return jdbcTemplate.query(ROUTE_SELECT + " WHERE " + PUBLISHED, ROW_MAPPER);
	}

	public record PublishedRoute(RouteSnapshot route, String bizId) {
	}

}
