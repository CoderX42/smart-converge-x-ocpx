package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.dto.CatalogViews;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdxAdapterService {

	private final JdbcTemplate jdbcTemplate;

	public AdxAdapterService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<CatalogViews.Adapter> list() {
		return jdbcTemplate.query("""
				SELECT adapter_code,adapter_name,direction,status,capabilities_json
				FROM scx_ocpx_adapter ORDER BY direction,adapter_code
				""", (rs, rowNum) -> new CatalogViews.Adapter(rs.getString(1), rs.getString(2), rs.getString(3),
				rs.getString(4), rs.getString(5)));
	}

	public void requireEnabled(String adapterCode, String direction) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_adapter
				WHERE adapter_code=? AND direction=? AND status='ACTIVE'
				""", Long.class, adapterCode, direction);
		if (count == null || count == 0) {
			throw new IllegalArgumentException("适配器未注册或未启用：" + adapterCode);
		}
	}

}
