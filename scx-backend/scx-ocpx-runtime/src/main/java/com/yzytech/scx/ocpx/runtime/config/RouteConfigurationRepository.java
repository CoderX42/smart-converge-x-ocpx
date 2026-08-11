package com.yzytech.scx.ocpx.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Reads only immutable S7 snapshots that have instance distribution evidence. */
@Repository
public class RouteConfigurationRepository {

	private static final String ACTIVE_ROUTE_SELECT = """
			SELECT v.route_code_hash,v.runtime_snapshot_ciphertext,m.biz_id
			FROM scx_ocpx_route_version v
			JOIN scx_ocpx_route_runtime_state s ON s.route_version_id=v.id
			JOIN scx_ocpx_route_distribution d ON d.route_version_id=v.id AND d.target_group='DEFAULT'
			JOIN scx_ocpx_monitor_link m ON m.id=v.monitor_id
			WHERE v.status='PUBLISHED' AND d.status IN ('ACTIVE','DEGRADED')
			 AND s.deny_at IS NULL
			 AND ((s.acceptance_status='ACTIVE' AND (s.accept_from IS NULL OR s.accept_from<=NOW(3)))
			      OR (s.acceptance_status='OVERLAP' AND s.accept_until>NOW(3)))
			""";

	private final JdbcTemplate jdbcTemplate;

	private final ProtocolCrypto protocolCrypto;

	private final ObjectMapper objectMapper;

	public RouteConfigurationRepository(JdbcTemplate jdbcTemplate, ProtocolCrypto protocolCrypto,
			ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.protocolCrypto = protocolCrypto;
		this.objectMapper = objectMapper;
	}

	public Optional<RouteSnapshot> findPublished(String channelCode, String routeReference) {
		return jdbcTemplate
			.query(ACTIVE_ROUTE_SELECT + " AND (m.id=? OR m.biz_id=?)", this::mapRoute, routeReference, routeReference)
			.stream()
			.map(PublishedRoute::route)
			.filter(route -> channelCode.equals(route.channelCode()))
			.findFirst();
	}

	public Optional<RouteSnapshot> findByRouteCode(String routeCode) {
		String hash = protocolCrypto.blindIndex("OCPX_ROUTE_CODE", routeCode);
		return jdbcTemplate.query(ACTIVE_ROUTE_SELECT + " AND v.route_code_hash=? LIMIT 1", this::mapRoute, hash)
			.stream()
			.map(PublishedRoute::route)
			.findFirst();
	}

	public List<PublishedRoute> findAllPublished() {
		return jdbcTemplate.query(ACTIVE_ROUTE_SELECT, this::mapRoute);
	}

	/**
	 * Lists immutable versions awaiting application by this runtime instance. The
	 * returned metadata contains no route code, URL or credential value.
	 */
	public List<DistributableRoute> findDistributable(int limit) {
		int boundedLimit = Math.max(1, Math.min(limit, 100));
		return jdbcTemplate.query("""
				SELECT d.id,v.id,v.route_version
				FROM scx_ocpx_route_distribution d
				JOIN scx_ocpx_route_version v ON v.id=d.route_version_id
				JOIN scx_ocpx_route_runtime_state s ON s.route_version_id=v.id
				WHERE v.status='PUBLISHED' AND d.target_group='DEFAULT'
				 AND d.status IN ('PENDING','DISTRIBUTING','DEGRADED')
				 AND s.acceptance_status='PENDING_DISTRIBUTION' AND s.deny_at IS NULL
				ORDER BY d.create_time LIMIT ?
				""", (rs, rowNum) -> new DistributableRoute(rs.getString(1), rs.getString(2), rs.getLong(3)),
				boundedLimit);
	}

	public Optional<DistributableRoute> findDistributable(String routeVersionId, long routeVersion) {
		return jdbcTemplate
			.query("""
					SELECT d.id,v.id,v.route_version
					FROM scx_ocpx_route_distribution d
					JOIN scx_ocpx_route_version v ON v.id=d.route_version_id
					JOIN scx_ocpx_route_runtime_state s ON s.route_version_id=v.id
					WHERE v.id=? AND v.route_version=? AND v.status='PUBLISHED' AND d.target_group='DEFAULT'
					 AND d.status IN ('PENDING','DISTRIBUTING','DEGRADED')
					 AND s.acceptance_status='PENDING_DISTRIBUTION' AND s.deny_at IS NULL
					""", (rs, rowNum) -> new DistributableRoute(rs.getString(1), rs.getString(2), rs.getLong(3)),
					routeVersionId, routeVersion)
			.stream()
			.findFirst();
	}

	/** Validates and decrypts an exact immutable version before acknowledging apply. */
	public RouteSnapshot validateDistributableSnapshot(DistributableRoute candidate) {
		List<RouteSnapshot> snapshots = jdbcTemplate.query("""
				SELECT v.runtime_snapshot_ciphertext
				FROM scx_ocpx_route_distribution d
				JOIN scx_ocpx_route_version v ON v.id=d.route_version_id
				JOIN scx_ocpx_route_runtime_state s ON s.route_version_id=v.id
				WHERE d.id=? AND v.id=? AND v.route_version=? AND v.status='PUBLISHED'
				 AND d.status IN ('PENDING','DISTRIBUTING','DEGRADED')
				 AND s.acceptance_status='PENDING_DISTRIBUTION' AND s.deny_at IS NULL
				FOR UPDATE
				""", (rs, rowNum) -> decodeSnapshot(rs.getString(1)), candidate.distributionId(),
				candidate.routeVersionId(), candidate.routeVersion());
		if (snapshots.size() != 1 || snapshots.get(0).routeVersion() != candidate.routeVersion()) {
			throw new IllegalStateException("distributable route version changed before apply");
		}
		return snapshots.get(0);
	}

	public Optional<RouteSnapshot> findPublishedById(String monitorId) {
		return jdbcTemplate
			.query(ACTIVE_ROUTE_SELECT + " AND v.monitor_id=? ORDER BY v.route_version DESC LIMIT 1", this::mapRoute,
					monitorId)
			.stream()
			.map(PublishedRoute::route)
			.findFirst();
	}

	private PublishedRoute mapRoute(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		try {
			String plaintext = protocolCrypto.decrypt(rs.getString(2));
			JsonNode root = objectMapper.readTree(plaintext);
			RouteSnapshot route = decodeSnapshot(root);
			return new PublishedRoute(route, rs.getString(3), rs.getString(1), text(root, "linkUrlTemplate"),
					text(root, "macroSchemaJson"));
		}
		catch (IllegalStateException error) {
			throw error;
		}
		catch (Exception error) {
			throw new IllegalStateException("immutable route snapshot cannot be decoded", error);
		}
	}

	RouteSnapshot decodeSnapshot(String ciphertext) {
		try {
			return decodeSnapshot(objectMapper.readTree(protocolCrypto.decrypt(ciphertext)));
		}
		catch (IllegalStateException error) {
			throw error;
		}
		catch (Exception error) {
			throw new IllegalStateException("immutable route snapshot cannot be decoded", error);
		}
	}

	private RouteSnapshot decodeSnapshot(JsonNode root) throws Exception {
		JsonNode routeNode = root.path("route");
		if (routeNode.isMissingNode() || routeNode.isNull()) {
			throw new IllegalStateException("immutable route snapshot is incomplete");
		}
		RouteSnapshot route = objectMapper.treeToValue(routeNode, RouteSnapshot.class);
		if (route.eventSnapshotVersion() != 2 || !"SNAPSHOT".equals(route.eventMappingMode())
				|| route.eventMappings() == null || route.eventMappings().isEmpty()) {
			throw new IllegalStateException("route has no canonical event snapshot");
		}
		return route;
	}

	String resolveUpstreamSecret(String ciphertext, String storageMode, String status, LocalDateTime overlapUntil,
			String legacySecret) {
		if ("RETIRED".equals(status)
				|| ("OVERLAP".equals(status) && (overlapUntil == null || !overlapUntil.isAfter(LocalDateTime.now())))) {
			throw new IllegalStateException("Published advertiser credential revision is no longer valid");
		}
		if ("ENCRYPTED".equals(storageMode)) {
			if (ciphertext == null || ciphertext.isBlank()) {
				throw new IllegalStateException("Published advertiser credential revision is incomplete");
			}
			return protocolCrypto.decrypt(ciphertext);
		}
		return legacySecret;
	}

	private String text(JsonNode root, String field) {
		JsonNode value = root.path(field);
		return value.isTextual() ? value.asText() : null;
	}

	public record PublishedRoute(RouteSnapshot route, String bizId, String routeCodeHash, String destinationTemplate,
			String macroSchemaJson) {

		public PublishedRoute(RouteSnapshot route, String bizId) {
			this(route, bizId, null, null, null);
		}

		public PublishedRoute(RouteSnapshot route, String bizId, String routeCodeHash) {
			this(route, bizId, routeCodeHash, null, null);
		}
	}

	public record DistributableRoute(String distributionId, String routeVersionId, long routeVersion) {
	}

}
