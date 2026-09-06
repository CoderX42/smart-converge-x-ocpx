package com.yzytech.scx.ocpx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ScxOcpxEngineApplicationTest {

	@Test
	void keepsTheServletPortContextDefaultProfileAndLegacyJobSettings() throws Exception {
		PropertySource<?> source = load("application.yml");

		assertThat(source.getProperty("server.port")).isEqualTo(10000);
		assertThat(source.getProperty("server.servlet.context-path")).isEqualTo("/ocpx");
		assertThat(source.getProperty("spring.profiles.active")).isEqualTo("dev");
		assertThat(source.getProperty("spring.main.web-application-type")).isEqualTo("servlet");
		assertThat(source.getProperty("management.endpoints.web.exposure.include"))
			.isEqualTo("health,info,metrics,prometheus");
		assertThat(source.getProperty("bid.job.thread")).isEqualTo(1);
		assertThat(source.getProperty("ocpx.compatibility-report.enabled")).isEqualTo(false);
		assertThat(source.getProperty("ocpx.flow-log.success-sample-rate")).isEqualTo(0.1);
		assertThat(source.getProperty("ocpx.delivery.upstream.core-threads")).isEqualTo(32);
		assertThat(source.getProperty("ocpx.delivery.upstream.max-threads")).isEqualTo(64);
		assertThat(source.getProperty("ocpx.delivery.upstream.queue-capacity")).isEqualTo(200);
		assertThat(source.getProperty("ocpx.delivery.downstream.core-threads")).isEqualTo(32);
		assertThat(source.getProperty("ocpx.delivery.downstream.max-threads")).isEqualTo(64);
		assertThat(source.getProperty("ocpx.delivery.downstream.queue-capacity")).isEqualTo(200);
		assertThat(source.getProperty("app.rta.enabled")).isEqualTo(false);
		assertThat(source.getProperty("app.rta.mock-enabled")).isEqualTo(false);
		assertThat(source.getProperty("app.rta.admission-ttl-seconds")).isEqualTo(300);
		assertThat(source.getProperty("spring.datasource.url")).isNull();
	}

	@Test
	void separatesDevelopmentAndProductionConnections() throws Exception {
		PropertySource<?> development = load("application-dev.yml");
		PropertySource<?> production = load("application-prod.yml");

		assertThat(development.getProperty("spring.datasource.url"))
			.isNotEqualTo(production.getProperty("spring.datasource.url"));
		assertThat(development.getProperty("spring.data.redis.host"))
			.isNotEqualTo(production.getProperty("spring.data.redis.host"));
		assertThat(development.getProperty("domain.name")).isEqualTo("http://127.0.0.1:10000/ocpx");
		assertThat(production.getProperty("domain.name")).isEqualTo("https://ocpx.zhongsenyuedong.cn/ocpx");
		assertThat(development.getProperty("app.rta.enabled")).isEqualTo(true);
		assertThat(development.getProperty("app.rta.mock-enabled")).isEqualTo(true);
		assertThat(production.getProperty("app.rta.enabled")).isEqualTo(false);
		assertThat(production.getProperty("app.rta.mock-enabled")).isEqualTo(false);
		assertThat(development.getProperty("ocpx.aerim.callback-base-url")).isNull();
		assertThat(production.getProperty("ocpx.aerim.callback-base-url")).isNull();
	}

	private PropertySource<?> load(String resource) throws Exception {
		return new YamlPropertySourceLoader().load("engine", new ClassPathResource(resource)).get(0);
	}

}
