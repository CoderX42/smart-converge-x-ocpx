package com.yzytech.scx.ocpx.admin.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxConfigChangeServiceTest {

	@Test
	void advertiserChangesMarkPublishedRoutesWithoutDestroyingFrozenSnapshot() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(any(String.class), eq("credential rotated"), eq("advertiser-1"))).thenReturn(3);
		OcpxConfigChangeService service = new OcpxConfigChangeService(jdbcTemplate);

		assertThat(service.advertiserChanged("advertiser-1", "credential rotated")).isEqualTo(3);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).update(sql.capture(), eq("credential rotated"), eq("advertiser-1"));
		assertThat(sql.getValue()).contains("republish_required=1", "route_status='PUBLISHED'")
			.doesNotContain("published_upstream_feedback_url=NULL", "route_status='DRAFT'",
					"config_version=config_version+1");
	}

	@Test
	void productChangesOnlyMarkPublishedRoutesForLaterRepublish() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(any(String.class), eq("event defaults changed"), eq("product-1"))).thenReturn(2);
		OcpxConfigChangeService service = new OcpxConfigChangeService(jdbcTemplate);

		assertThat(service.productChanged("product-1", "event defaults changed")).isEqualTo(2);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).update(sql.capture(), eq("event defaults changed"), eq("product-1"));
		assertThat(sql.getValue())
			.contains("JOIN scx_ocpx_app_link", "l.product_id=?", "republish_required=1", "route_status='PUBLISHED'")
			.doesNotContain("route_status='DRAFT'", "config_version=config_version+1");
	}

	@Test
	void appLinkChangesOnlyMarkPublishedRoutesAndKeepTheFrozenLinkSnapshot() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(any(String.class), eq("template changed"), eq("app-link-1"))).thenReturn(2);
		OcpxConfigChangeService service = new OcpxConfigChangeService(jdbcTemplate);

		assertThat(service.appLinkChanged("app-link-1", "template changed")).isEqualTo(2);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).update(sql.capture(), eq("template changed"), eq("app-link-1"));
		assertThat(sql.getValue()).contains("app_link_id=?", "route_status='PUBLISHED'", "republish_required=1")
			.doesNotContain("published_app_link_url_template=NULL", "route_status='DRAFT'",
					"config_version=config_version+1");
	}

	@Test
	void mediaAccountChangesMarkOnlyDependentPublishedRoutes() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(any(String.class), eq("credential revision changed"), eq("media-1"))).thenReturn(4);
		OcpxConfigChangeService service = new OcpxConfigChangeService(jdbcTemplate);

		assertThat(service.mediaAccountChanged("media-1", "credential revision changed")).isEqualTo(4);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).update(sql.capture(), eq("credential revision changed"), eq("media-1"));
		assertThat(sql.getValue()).contains("media_account_id=?", "route_status='PUBLISHED'", "republish_required=1")
			.doesNotContain("published_media_account_id=NULL", "route_status='DRAFT'",
					"config_version=config_version+1");
	}

}
