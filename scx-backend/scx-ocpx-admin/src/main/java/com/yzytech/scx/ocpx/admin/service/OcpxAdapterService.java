package com.yzytech.scx.ocpx.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.admin.dto.CatalogViews;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class OcpxAdapterService {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	public OcpxAdapterService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
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

	public List<AdvertiserViews.AdapterCapability> advertiserCapabilities() {
		return jdbcTemplate.query("""
				SELECT adapter_code,adapter_name,capability_revision,capabilities_json
				FROM scx_ocpx_adapter WHERE direction='UPSTREAM' AND status='ACTIVE'
				ORDER BY adapter_code
				""",
				(rs, rowNum) -> advertiserCapability(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)));
	}

	public AdvertiserViews.AdapterCapability requireAdvertiserCapability(String adapterCode) {
		return advertiserCapabilities().stream()
			.filter(adapter -> adapter.adapterKey().equals(adapterCode))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("上游适配器未注册或未启用：" + adapterCode));
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
					stringSet(root.path("requiredUrlMacros")), stringGroups(root.path("requiredAnyUrlMacros")),
					root.path("trackingMacroSchemaVersion").asInt(0),
					trackingMacroPairs(root.path("trackingMacroPairs")));
		}
		catch (Exception error) {
			throw new IllegalStateException("适配器能力配置无效：" + adapterCode, error);
		}
	}

	private List<TrackingMacroPair> trackingMacroPairs(JsonNode values) {
		if (!values.isArray()) {
			return List.of();
		}
		return java.util.stream.StreamSupport.stream(values.spliterator(), false)
			.filter(JsonNode::isObject)
			.map(value -> new TrackingMacroPair(text(value, "key"), text(value, "macro"),
					value.path("required").asBoolean(false)))
			.filter(value -> value.key() != null && value.macro() != null)
			.toList();
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

	private AdvertiserViews.AdapterCapability advertiserCapability(String adapterCode, String adapterName, long version,
			String capabilitiesJson) {
		try {
			JsonNode root = objectMapper.readTree(capabilitiesJson);
			List<String> authTypes = root.path("authTypes").isArray()
					? java.util.stream.StreamSupport.stream(root.path("authTypes").spliterator(), false)
						.filter(JsonNode::isTextual)
						.map(JsonNode::asText)
						.toList()
					: root.path("auth").isTextual() ? List.of(root.path("auth").asText()) : List.of();
			List<String> eventVocabulary = root.path("feedbackEvents").isArray()
					? java.util.stream.StreamSupport.stream(root.path("feedbackEvents").spliterator(), false)
						.filter(JsonNode::isTextual)
						.map(JsonNode::asText)
						.toList()
					: List.of();
			return new AdvertiserViews.AdapterCapability(adapterCode, adapterName, version,
					!root.has("requiresBidUrl") || root.path("requiresBidUrl").asBoolean(true),
					root.path("callback").asBoolean(false), authTypes, eventVocabulary,
					root.path("timeoutMs").isInt() ? root.path("timeoutMs").asInt() : null);
		}
		catch (Exception error) {
			throw new IllegalStateException("适配器能力配置无效：" + adapterCode, error);
		}
	}

	/** 管理面发布所需的最小、协议无关能力快照。 */
	public record PublishedAdapter(String adapterCode, String direction, String feedbackMethod, String callbackMethod,
			Set<String> supportedMacros, Set<String> requiredUrlMacros, List<Set<String>> requiredAnyUrlMacros,
			int trackingMacroSchemaVersion, List<TrackingMacroPair> trackingMacroPairs) {

		public PublishedAdapter(String adapterCode, String direction, String feedbackMethod, String callbackMethod,
				Set<String> supportedMacros, Set<String> requiredUrlMacros, List<Set<String>> requiredAnyUrlMacros) {
			this(adapterCode, direction, feedbackMethod, callbackMethod, supportedMacros, requiredUrlMacros,
					requiredAnyUrlMacros, 0, List.of());
		}
	}

	public record TrackingMacroPair(String key, String macro, boolean required) {
	}

}
