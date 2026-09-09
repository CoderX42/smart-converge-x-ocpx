package com.yzytech.scx.ocpx.admin.rta;

import com.yzytech.scx.ocpx.admin.mapper.RtaJobsSqlMapper;
import com.yzytech.scx.ocpx.admin.mapper.RtaManagementSqlMapper;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.admin.service.AdvertiserScope;
import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Requires an explicit isolated MySQL URL; never reads dev/prod configuration. */
@EnabledIfSystemProperty(named = "rta.mysql.url", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/scx_rta_test.*")
class RtaPersistenceIntegrationTest {

	static Process process;
	static LettuceConnectionFactory factory;
	static StringRedisTemplate redis;
	static JdbcTemplate jdbc;
	static DataSourceTransactionManager tx;

	RtaJobs jobs;

	RtaManagement management;

	AdvertiserScope scope;

	@BeforeAll
	static void infrastructure() throws Exception {
		var ds = new DriverManagerDataSource(System.getProperty("rta.mysql.url"), "root", "");
		jdbc = new JdbcTemplate(ds);
		tx = new DataSourceTransactionManager(ds);
		jdbc.execute(
				"CREATE TABLE IF NOT EXISTS sys_user(id VARCHAR(64) PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
		String schema = Files.readString(Path.of("../scx-boot/src/main/resources/db/schema/scx_current_schema.sql"));
		for (String table : List.of("advertiser", "media", "promotion_link", "monitor_task", "rta_upstream",
				"rta_media_access", "rta_binding", "rta_log", "rta_hour_stat")) {
			var pattern = Pattern.compile("CREATE TABLE IF NOT EXISTS scx_ocpx_" + table + " \\(.*?;", Pattern.DOTALL);
			var match = pattern.matcher(schema);
			assertThat(match.find()).isTrue();
			jdbc.execute(match.group());
		}
		int port;
		try (var socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		process = new ProcessBuilder(System.getProperty("rta.redis.executable", "redis-server"), "--bind", "127.0.0.1",
				"--port", Integer.toString(port), "--save", "", "--appendonly", "no")
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
		factory = new LettuceConnectionFactory("127.0.0.1", port);
		factory.afterPropertiesSet();
		factory.start();
		redis = new StringRedisTemplate(factory);
		redis.afterPropertiesSet();
		for (int i = 0; i < 100; i++) {
			try (var conn = factory.getConnection()) {
				conn.ping();
				return;
			}
			catch (Exception e) {
				Thread.sleep(20);
			}
		}
		throw new IllegalStateException("isolated Redis unavailable");
	}

	@AfterAll
	static void close() {
		if (factory != null)
			factory.destroy();
		if (process != null)
			process.destroy();
	}

	@BeforeEach
	void setup() {
		try (var c = factory.getConnection()) {
			c.serverCommands().flushDb();
		}
		for (String table : List.of("rta_binding", "rta_media_access", "rta_log", "rta_hour_stat", "rta_upstream",
				"monitor_task", "promotion_link", "media", "advertiser"))
			jdbc.update("DELETE FROM scx_ocpx_" + table);
		jdbc.update("INSERT INTO scx_ocpx_advertiser(id,advertiser_name) VALUES('adv','Advertiser'),('other','Other')");
		jdbc.update("INSERT INTO scx_ocpx_media(id,media_name,uri) VALUES('media','Original media','/media')");
		jdbc.update(
				"INSERT INTO scx_ocpx_promotion_link(id,advertiser_id,link_name,platform,link_url) VALUES('link','adv','Link',1,'https://a.test')");
		jdbc.update(
				"INSERT INTO scx_ocpx_monitor_task(id,monitor_name,media_id,advertiser_id,promotion_link_id) VALUES('A','Task A','media','adv','link'),('B','Task B','media','adv','link')");
		scope = mock(AdvertiserScope.class);
		when(scope.constrain(any())).thenAnswer(a -> a.getArgument(0));
		management = new RtaManagement(XmlMapperTestSupport.mapper(RtaManagementSqlMapper.class, jdbc), scope, redis);
		ReflectionTestUtils.setField(management, "allowedHosts", List.of("a.test", "m.test"));
		ReflectionTestUtils.setField(management, "publicBaseUrl", "https://scx.test/ocpx");
		jobs = new RtaJobs(redis, XmlMapperTestSupport.mapper(RtaJobsSqlMapper.class, jdbc), tx);
	}

	void save(String kind, Map<String, Object> data) {
		new TransactionTemplate(tx).executeWithoutResult(status -> management.save(kind, data));
	}

	@Test
	void existingMediaUniquenessOwnershipOptimisticLockAndCurrentNames() {
		save("media", Map.of("mediaId", "media", "notifyUrl", "https://m.test/notify", "credentialRef", "media-key",
				"enabled", true));
		assertThatThrownBy(() -> save("media",
				Map.of("mediaId", "media", "notifyUrl", "https://m.test/notify", "credentialRef", "other-key")))
			.isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
		save("upstreams", Map.of("advertiserId", "adv", "name", "Async", "accessCode", "access", "endpoint",
				"https://a.test/request", "credentialRef", "upstream-key", "enabled", true));
		String upstream = jdbc.queryForObject("SELECT id FROM scx_ocpx_rta_upstream", String.class);
		save("bindings", Map.of("monitorTaskId", "A", "upstreamId", upstream, "productId", "product", "externalTaskId",
				"task", "enabled", true));
		assertThatThrownBy(() -> save("bindings", Map.of("monitorTaskId", "B", "upstreamId", upstream, "productId",
				"product", "externalTaskId", "task", "enabled", true)))
			.hasMessageContaining("歧义");
		assertThatThrownBy(() -> save("bindings", Map.of("monitorTaskId", "A", "upstreamId", upstream, "productId",
				"product", "externalTaskId", "task", "enabled", true, "version", 99)))
			.hasMessageContaining("配置已变更");
		jdbc.update("UPDATE scx_ocpx_media SET media_name='Renamed media' WHERE id='media'");
		assertThat(management.list("media", Map.of()).records().get(0)).containsEntry("mediaName", "Renamed media");
		assertThat(management.list("bindings", Map.of("monitorTaskId", "A")).records().get(0))
			.containsEntry("mediaName", "Renamed media");
		when(scope.constrain(any())).thenReturn("other");
		assertThat(management.list("media", Map.of()).total()).isZero();
		assertThat(management.list("bindings", Map.of()).total()).isZero();
		assertThat(management.options("tasks", Map.of())).isEmpty();
		doThrow(new IllegalStateException("forbidden")).when(scope).requireOwner("adv");
		assertThatThrownBy(() -> save("bindings", Map.of("monitorTaskId", "A", "upstreamId", upstream, "productId", "p",
				"externalTaskId", "t", "version", 1)))
			.hasMessage("forbidden");
	}

	@Test
	void multipleAdvertiserAccessesProtocolFieldsAndPlanBinding() {
		ReflectionTestUtils.setField(management, "allowedHosts", List.of("a.test", "m.test", "rta.adapi.cn"));
		save("media", Map.of("mediaId", "media", "notifyUrl", "https://m.test/notify", "credentialRef", "media-key",
				"enabled", true));
		for (String aid : List.of("5103", "5104"))
			save("upstreams", Map.of("advertiserId", "adv", "name", "Weifeng " + aid, "protocol", "WEIFENG_RTA",
					"protocolConfig", Map.of("aid", aid, "did", "zsyd"), "enabled", true));
		var accesses = management.list("upstreams", Map.of("advertiserId", "adv")).records();
		assertThat(accesses).hasSize(2);
		var access = accesses.stream().filter(r -> r.get("name").equals("Weifeng 5103")).findFirst().orElseThrow();
		assertThat(access).doesNotContainKey("callbackUrl");
		assertThat(access.get("credentialRef")).isEqualTo("");
		String id = access.get("id").toString();
		assertThatThrownBy(() -> save("bindings", Map.of("monitorTaskId", "A", "upstreamId", id, "enabled", true)))
			.hasMessageContaining("计划参数");
		jdbc.update(
				"UPDATE scx_ocpx_promotion_link SET link_url='https://ad.adapi.cn/click?aid=5104&did=zsyd' WHERE id='link'");
		assertThatThrownBy(() -> save("bindings", Map.of("monitorTaskId", "A", "upstreamId", id, "enabled", true)))
			.hasMessageContaining("不一致");
		jdbc.update(
				"UPDATE scx_ocpx_promotion_link SET link_url='https://ad.adapi.cn/click?aid=5103&did=zsyd' WHERE id='link'");
		save("bindings", Map.of("monitorTaskId", "A", "upstreamId", id, "enabled", true));
		var binding = management.list("bindings", Map.of("monitorTaskId", "A")).records().get(0);
		assertThat(binding.get("productId")).isNull();
		assertThat(binding.get("externalTaskId")).isNull();
		assertThat(binding.get("protocol")).isEqualTo("WEIFENG_RTA");
		assertThat(management.options("upstreams", Map.of("advertiserId", "other"))).isEmpty();
		assertThatThrownBy(() -> save("upstreams",
				Map.of("id", id, "advertiserId", "adv", "accessCode", access.get("accessCode"), "name", "Wrong",
						"protocol", "GENERIC_ASYNC", "endpoint", "https://a.test/request", "credentialRef", "ref",
						"version", 1)))
			.hasMessageContaining("协议不可修改");
		assertThatThrownBy(
				() -> save("upstreams",
						Map.of("advertiserId", "adv", "name", "Bad", "protocol", "WEIFENG_RTA", "protocolConfig",
								Map.of("aid", "5103", "did", "zsyd", "secret", "never-store"))))
			.hasMessageContaining("字段");
		assertThatThrownBy(() -> save("media", Map.of("mediaId", "media", "notifyUrl", "https://m.test/notify",
				"credentialRef", "ref", "secret", "never-store")))
			.hasMessageContaining("未知");
	}

	String stream = RtaContract.PREFIX + "logs";

	String payload(String id, long time) {
		return JSON.toJSONString(new RtaContract.Log(id, "a".repeat(32), "request", "media", "adv", "upstream", "A",
				"UPSTREAM_SUBMIT", "FAILED", "HTTP_503", 10, time));
	}

	@Test
	void pausedJobRecoveryDatabaseFailureAckReplayPendingDlqAndCleanup() {
		String payload = payload("b".repeat(32), System.currentTimeMillis());
		var id = redis.opsForStream().add(stream, Map.of("payload", payload));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scx_ocpx_rta_log", Long.class)).isZero();
		RtaJobsSqlMapper failing = mock(RtaJobsSqlMapper.class);
		doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline")).when(failing)
			.insertLogs(anyList());
		assertThatThrownBy(() -> new RtaJobs(redis, failing, tx).flushLogs()).hasMessage("offline");
		assertThat(redis.opsForStream().pending(stream, "sql-writers").getTotalPendingMessages()).isEqualTo(1);
		redis.opsForStream()
			.claim(stream, "sql-writers", "crashed",
					RedisStreamCommands.XClaimOptions.minIdle(Duration.ZERO).ids(id).idle(Duration.ofSeconds(31)));
		jobs.flushLogs();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scx_ocpx_rta_log", Long.class)).isEqualTo(1);
		// Replays the exact producer ID, as after SQL commit followed by failed ACK.
		redis.opsForStream().add(stream, Map.of("payload", payload));
		redis.opsForStream().add(stream, Map.of("payload", "{broken"));
		jobs.flushLogs();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scx_ocpx_rta_log", Long.class)).isEqualTo(1);
		assertThat(redis.opsForStream().pending(stream, "sql-writers").getTotalPendingMessages()).isZero();
		assertThat(redis.opsForStream().size(stream + ":dlq")).isEqualTo(1);
		jdbc.update("UPDATE scx_ocpx_rta_log SET event_time=DATE_SUB(NOW(),INTERVAL 25 HOUR)");
		jobs.cleanupLogs();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scx_ocpx_rta_log", Long.class)).isZero();
	}

	@Test
	void metricSnapshotsRejectOlderVersionsAndAggregateByDay() {
		String key = RtaContract.PREFIX + "metric:test";
		long hour = (System.currentTimeMillis() / 3600000) * 3600000;
		var snapshot = new HashMap<String, String>(Map.of("timeHour", Long.toString(hour), "mediaId", "media",
				"advertiserId", "adv", "taskId", "A", "upstreamId", "upstream", "version", "3", "requestCount", "2",
				"allowCount", "1", "denyCount", "1"));
		redis.opsForHash().putAll(key, snapshot);
		redis.opsForSet().add(RtaContract.PREFIX + "metric-dirty", key);
		assertThat(redis.getExpire(key)).isEqualTo(-1);
		jobs.flushMetrics();
		assertThat(redis.getExpire(key)).isPositive();
		snapshot.put("version", "2");
		snapshot.put("requestCount", "1");
		snapshot.put("denyCount", "0");
		redis.opsForHash().putAll(key, snapshot);
		redis.opsForSet().add(RtaContract.PREFIX + "metric-dirty", key);
		jobs.flushMetrics();
		assertThat(jdbc.queryForObject("SELECT request_count FROM scx_ocpx_rta_hour_stat", Long.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT version FROM scx_ocpx_rta_hour_stat", Long.class)).isEqualTo(3);
		var report = management.list("reports", Map.of("grain", "DAY"));
		assertThat(report.total()).isEqualTo(1);
		assertThat(report.records().get(0).get("allowRate")).isEqualTo(0.5);
		snapshot.put("version", "4");
		snapshot.put("requestCount", "3");
		snapshot.put("denyCount", "2");
		redis.opsForHash().putAll(key, snapshot);
		redis.opsForSet().add(RtaContract.PREFIX + "metric-dirty", key);
		jobs.flushMetrics();
		assertThat(jdbc.queryForObject("SELECT request_count FROM scx_ocpx_rta_hour_stat", Long.class)).isEqualTo(3);
	}

	@Test
	void oppoMediaHasStableStrategyIdsAndScopedConnectionDetails() {
		ReflectionTestUtils.setField(management, "allowedHosts", List.of("rta.adapi.cn"));
		save("media",
				Map.of("mediaId", "media", "protocol", "OPPO", "credentialRef", "oppo-code", "enabled", true,
						"protocolConfig",
						Map.of("ownerMode", "EMPTY", "timeoutMs", "1000", "uploadCredentialRef", "oppo-upload")));
		save("upstreams", Map.of("advertiserId", "adv", "name", "Weifeng", "protocol", "WEIFENG_RTA", "protocolConfig",
				Map.of("aid", "5103", "did", "zsyd"), "enabled", true));
		String upstream = jdbc.queryForObject("SELECT id FROM scx_ocpx_rta_upstream", String.class);
		jdbc.update(
				"UPDATE scx_ocpx_promotion_link SET link_url='https://ad.adapi.cn/click?aid=5103&did=zsyd' WHERE id='link'");
		save("bindings",
				Map.of("monitorTaskId", "A", "upstreamId", upstream, "enabled", true, "oppoPkg", "com.example.app"));
		save("bindings", Map.of("monitorTaskId", "B", "upstreamId", upstream, "enabled", true));
		var row = management.list("bindings", Map.of("monitorTaskId", "A")).records().get(0);
		assertThat(((Number) row.get("strategyId")).intValue()).isPositive();
		assertThat(row.get("clickUrl").toString()).contains("/oppo/click/oppo-code/", "__REQ__", "__OAID__",
				"__ADID__");
		assertThat(row.get("oppoPkg")).isEqualTo("com.example.app");
		var testRoute = XmlMapperTestSupport.mapper(RtaManagementSqlMapper.class, jdbc).selectRtaTestRoute("A");
		assertThat(testRoute).containsEntry("task_name", "Task A")
			.containsEntry("media_protocol", "OPPO")
			.containsEntry("upstream_protocol", "WEIFENG_RTA");
		assertThat(((Number) testRoute.get("strategy_id")).intValue())
			.isEqualTo(((Number) row.get("strategyId")).intValue());
		var media = management.list("media", Map.of("mediaId", "media")).records().get(0);
		assertThat(media).containsEntry("requestUrl", "https://scx.test/ocpx/rta/oppo/oppo-code")
			.containsEntry("notifyUrl", "");
		assertThat(media).doesNotContainKey("secret");
		save("bindings", Map.of("monitorTaskId", "A", "upstreamId", upstream, "enabled", true, "oppoPkg",
				"com.example.updated", "version", 1));
		assertThat(management.list("bindings", Map.of("monitorTaskId", "A")).records().get(0).get("strategyId"))
			.isEqualTo(row.get("strategyId"));
		assertThatThrownBy(() -> save("media", Map.of("mediaId", "media", "protocol", "OPPO", "credentialRef",
				"oppo-code", "version", 1, "protocolConfig", Map.of("secret", "forbidden"))))
			.hasMessageContaining("字段");
		when(scope.constrain(any())).thenReturn("other");
		assertThat(management.list("bindings", Map.of("monitorTaskId", "A")).records()).isEmpty();
	}

}
