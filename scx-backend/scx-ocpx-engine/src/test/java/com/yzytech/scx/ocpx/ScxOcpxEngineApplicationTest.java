package com.yzytech.scx.ocpx;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScxOcpxEngineApplicationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void startsTheReactiveApplication() {
		assertThat(port).isPositive();
		assertThat(applicationContext).isNotNull();
	}

	@Test
	void keepsTheDefaultPortAndContextPath() throws IOException {
		PropertySource<?> source = new YamlPropertySourceLoader()
			.load("engine", new ClassPathResource("application.yml"))
			.get(0);

		assertThat(source.getProperty("server.port")).isEqualTo(10000);
		assertThat(source.getProperty("spring.webflux.base-path")).isEqualTo("/ocpx");
	}

}
