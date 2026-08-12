package com.yzytech.scx.ocpx.runtime.config;

import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({ OcpxRuntimeProperties.class, OcpxCryptoProperties.class,
		PartnerCredentialProperties.class })
public class OcpxRuntimeConfiguration {

	@Bean
	ProtocolCrypto protocolCrypto(OcpxCryptoProperties properties) {
		return new ProtocolCrypto(properties);
	}

	@Bean
	WebClient ocpxWebClient(WebClient.Builder builder) {
		ConnectionProvider connectionProvider = ConnectionProvider.builder("ocpx-http")
			.maxConnections(512)
			.pendingAcquireMaxCount(1024)
			.pendingAcquireTimeout(Duration.ofMillis(100))
			.maxIdleTime(Duration.ofSeconds(30))
			.build();
		HttpClient httpClient = HttpClient.create(connectionProvider)
			.followRedirect(false)
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
			.responseTimeout(Duration.ofSeconds(3));
		ExchangeStrategies strategies = ExchangeStrategies.builder()
			.codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(4096))
			.build();
		return builder.clientConnector(new ReactorClientHttpConnector(httpClient))
			.exchangeStrategies(strategies)
			.build();
	}

}
