package com.yzytech.scx.ocpx.runtime.config;

import com.yzytech.scx.ocpx.contract.OcpxPartnerContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Reads the complete active relationship directly from the four business tables. */
@Repository
public class PartnerContextRepository {

	private static final String SELECT = """
			SELECT a.id,a.advertiser_code,p.id,m.id,m.media_code,t.id,t.task_code,
			       a.adapter_code,a.credential_config_key,a.credential_version,a.bid_url,
			       m.adapter_code,m.auth_type,m.credential_config_key,COALESCE(m.credential_version,0),
			       m.callback_url,p.link_url_template,t.config_version
			FROM scx_ocpx_monitor_task t
			JOIN scx_ocpx_advertiser a ON a.id=t.advertiser_id AND a.status='ACTIVE' AND a.deleted=0
			JOIN scx_ocpx_promotion_link p ON p.id=t.promotion_link_id AND p.advertiser_id=t.advertiser_id
			  AND p.status='ACTIVE' AND p.deleted=0
			JOIN scx_ocpx_media m ON m.id=t.media_id AND m.status='ACTIVE' AND m.deleted=0
			WHERE t.status='ACTIVE' AND t.deleted=0
			""";

	private final JdbcTemplate jdbcTemplate;

	public PartnerContextRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<OcpxPartnerContext> findForFeedback(String mediaCode, String monitorTaskId) {
		return query(" AND t.id=? AND m.media_code=? LIMIT 1", monitorTaskId, mediaCode);
	}

	public Optional<OcpxPartnerContext> findForCallback(String advertiserCode, String monitorTaskId) {
		return query(" AND t.id=? AND a.advertiser_code=? LIMIT 1", monitorTaskId, advertiserCode);
	}

	public Optional<OcpxPartnerContext> findByTaskCode(String taskCode) {
		return query(" AND t.task_code=? LIMIT 1", taskCode);
	}

	private Optional<OcpxPartnerContext> query(String suffix, Object... args) {
		return jdbcTemplate.query(SELECT + suffix, (rs, rowNum) -> new OcpxPartnerContext(rs.getString(1),
				rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
				rs.getString(7), rs.getString(8), rs.getString(9), rs.getLong(10), rs.getString(11),
				rs.getString(12), rs.getString(13), rs.getString(14), rs.getLong(15), rs.getString(16),
				rs.getString(17), rs.getLong(18)), args).stream().findFirst();
	}

}
