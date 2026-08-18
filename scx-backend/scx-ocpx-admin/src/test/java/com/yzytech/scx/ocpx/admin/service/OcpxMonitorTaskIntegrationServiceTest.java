package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskIntegrationRequests;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxMonitorTaskIntegrationServiceTest {

	private OcpxMonitorTaskMapper taskMapper;

	private AdvertiserScope advertiserScope;

	private JdbcTemplate jdbcTemplate;

	private OcpxIntegrationCallbackHttpClient httpClient;

	private OcpxMonitorTaskIntegrationService service;

	@BeforeEach
	void setUp() {
		taskMapper = mock(OcpxMonitorTaskMapper.class);
		advertiserScope = mock(AdvertiserScope.class);
		jdbcTemplate = mock(JdbcTemplate.class);
		httpClient = mock(OcpxIntegrationCallbackHttpClient.class);
		OcpxTrackingProperties trackingProperties = new OcpxTrackingProperties();
		trackingProperties.setEngineBaseUrl("https://tracking.example/ocpx");
		service = new OcpxMonitorTaskIntegrationService(taskMapper, advertiserScope, jdbcTemplate, httpClient,
				trackingProperties);

		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-1");
		task.setAdvertiserId("advertiser-1");
		task.setPromotionLinkId("link-1");
		task.setMediaId("media-1");
		when(taskMapper.selectById("task-1")).thenReturn(task);
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsManualDeliveryToTheSignedInternalTestCollector() throws Exception {
		String callbackUrl = "https://tracking.example/ocpx/test-callback"
				+ "?request_id=SCX-UAT-20260818141421-1f031f39925c&token=0123456789abcdef0123456789abcdef"
				+ "&mId=media-1&tId=task-1&aId=advertiser-1&lId=link-1";
		ResultSet resultSet = feedback(callbackUrl);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(44L), eq("task-1"))).thenAnswer(invocation -> {
			RowMapper<Object> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(resultSet, 0));
		});

		MonitorTaskIntegrationRequests.Callback request = new MonitorTaskIntegrationRequests.Callback(44L, "ACTIVATE",
				null);

		assertThatThrownBy(() -> service.sendCallback("task-1", request)).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("Engine 自动签名投递")
			.hasMessageContaining("不能通过媒体联调手工发送");
		verify(httpClient, never()).get(any());
	}

	private ResultSet feedback(String callbackUrl) throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString(1)).thenReturn("SCX-UAT-20260818141421-1f031f39925c");
		when(resultSet.getString(2)).thenReturn(callbackUrl);
		when(resultSet.getString(3)).thenReturn("advertiser-1");
		when(resultSet.getString(4)).thenReturn("link-1");
		when(resultSet.getString(5)).thenReturn("media-1");
		when(resultSet.getString(6)).thenReturn("task-1");
		return resultSet;
	}

}
