package com.yzytech.scx.adx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.adx.dto.CatalogViews;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdxAdapterService {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	public AdxAdapterService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public List<CatalogViews.Adapter> list() {
		return jdbcTemplate.query("""
				SELECT adapter_code,adapter_name,direction,status,capabilities_json
				FROM scx_ocpx_adapter ORDER BY direction,adapter_code
				""", (rs, rowNum) -> new CatalogViews.Adapter(rs.getString(1), rs.getString(2), rs.getString(3),
				rs.getString(4), rs.getString(5)));
	}

	/**
	 * 读取发布校验需要的 Adapter 能力。
	 *
	 * <p>
	 * 路由发布服务只理解统一能力字段，不再通过 COCO/TEMPLATE_GET 的 if 分支识别私有协议。
	 * </p>
	 */
	public PublishedAdapter requireEnabled(String adapterCode, String direction) {
		List<PublishedAdapter> adapters = jdbcTemplate.query("""
				SELECT adapter_code,direction,capabilities_json FROM scx_ocpx_adapter
				WHERE adapter_code=? AND direction=? AND status='ACTIVE'
				""", (rs, rowNum) -> parse(rs.getString(1), rs.getString(2), rs.getString(3)), adapterCode, direction);
		if (adapters.isEmpty()) {
			throw new IllegalArgumentException("适配器未注册或未启用：" + adapterCode);
		}
		return adapters.get(0);
	}

	PublishedAdapter parse(String adapterCode, String direction, String capabilitiesJson) {
		try {
			JsonNode root = objectMapper.readTree(capabilitiesJson);
			return new PublishedAdapter(adapterCode, direction, text(root, "feedbackMethod"),
					text(root, "callbackMethod"), stringSet(root.path("supportedMacros")),
					stringSet(root.path("requiredUrlMacros")), stringGroups(root.path("requiredAnyUrlMacros")));
		}
		catch (Exception error) {
			throw new IllegalStateException("适配器能力配置无效：" + adapterCode, error);
		}
	}

	private String text(JsonNode root, String field) {
		JsonNode value = root.path(field);
		return value.isTextual() ? value.asText() : null;
	}

	private Set<String> stringSet(JsonNode values) {
		Set<String> result = new LinkedHashSet<>();
		if (values.isArray()) {
			values.forEach(value -> {
				if (value.isTextual() && !value.asText().isBlank()) {
					result.add(value.asText());
				}
			});
		}
		return Set.copyOf(result);
	}

	private List<Set<String>> stringGroups(JsonNode groups) {
		if (!groups.isArray()) {
			return List.of();
		}
		return java.util.stream.StreamSupport.stream(groups.spliterator(), false)
			.map(this::stringSet)
			.filter(group -> !group.isEmpty())
			.toList();
	}

	/** 管理面发布所需的最小、协议无关能力快照。 */
	public record PublishedAdapter(String adapterCode, String direction, String feedbackMethod, String callbackMethod,
			Set<String> supportedMacros, Set<String> requiredUrlMacros, List<Set<String>> requiredAnyUrlMacros) {
	}

}
