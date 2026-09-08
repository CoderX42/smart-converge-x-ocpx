package com.yzytech.scx.ocpx;

import com.yzytech.scx.ocpx.callback.delivery.DownstreamDeliveryDispatcher;
import com.yzytech.scx.ocpx.configuration.index.IndexDataLoader;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryDispatcher;
import com.yzytech.scx.ocpx.oppo.callback.OppoUploadActiveDataDeliveryService;
import com.yzytech.scx.ocpx.persistence.mapper.OcpxEventFactSqlMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Start the complete Engine bean graph and HTTP server without external data or scheduled
 * work.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.profiles.active=startup-test", "server.address=127.0.0.1",
				"spring.datasource.url=jdbc:mysql://127.0.0.1:1/scx_startup_test", "spring.datasource.username=test",
				"spring.datasource.password=test", "spring.datasource.hikari.initialization-fail-timeout=-1",
				"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1",
				"domain.name=http://127.0.0.1:10000/ocpx", "app.callback-crypto.keys.v1=0123456789abcdef",
				"app.callback-crypto.active-key-id=v1", "management.endpoint.health.probes.enabled=true",
				"management.health.db.enabled=false", "management.health.redis.enabled=false",
				"ocpx.delivery.downstream.core-threads=2", "ocpx.delivery.downstream.max-threads=3",
				"ocpx.delivery.downstream.queue-capacity=7" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScxOcpxEngineStartupTest {

	@MockitoBean
	private IndexDataLoader indexDataLoader;

	@MockitoBean(name = "taskScheduler")
	private ThreadPoolTaskScheduler taskScheduler;

	@MockitoBean(name = "rtaScheduler")
	private ThreadPoolTaskScheduler rtaScheduler;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private TestRestTemplate http;

	@LocalServerPort
	private int port;

	@Test
	void startsCompleteApplicationWithConfiguredDeliveryServicesAndXmlMappers() {
		assertThat(context.getBean(DownstreamDeliveryDispatcher.class)).isNotNull();
		assertThat(ReflectionTestUtils.getField(DownstreamDeliveryDispatcher.class, "oppoDeliveryService"))
			.isSameAs(context.getBean(OppoUploadActiveDataDeliveryService.class));
		ThreadPoolExecutor executor = (ThreadPoolExecutor) DownstreamDeliveryDispatcher.EXECUTORS;
		assertThat(executor.getCorePoolSize()).isEqualTo(2);
		assertThat(executor.getMaximumPoolSize()).isEqualTo(3);
		assertThat(executor.getQueue().remainingCapacity()).isEqualTo(7);
		assertThat(context.getBean(SqlSessionFactory.class)
			.getConfiguration()
			.hasStatement(OcpxEventFactSqlMapper.class.getName() + ".insertInsertFactEventFact")).isTrue();
		verify(indexDataLoader).init();
		var health = http.getForEntity("http://127.0.0.1:" + port + "/ocpx/actuator/health", String.class);
		assertThat(health.getStatusCode().value()).isEqualTo(200);
		assertThat(health.getBody()).contains("\"status\":\"UP\"");
	}

	@AfterAll
	static void closeDeliveryExecutors() {
		DownstreamDeliveryDispatcher.EXECUTORS.shutdownNow();
		UpstreamDeliveryDispatcher.EXECUTORS.shutdownNow();
	}

}
