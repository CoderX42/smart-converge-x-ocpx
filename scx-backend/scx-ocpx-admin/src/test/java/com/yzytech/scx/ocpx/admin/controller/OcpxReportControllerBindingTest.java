package com.yzytech.scx.ocpx.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import com.yzytech.scx.ocpx.admin.service.OcpxDashboardService;
import com.yzytech.scx.ocpx.admin.service.OcpxReportExportService;
import com.yzytech.scx.ocpx.admin.service.OcpxReportService;
import com.yzytech.scx.ocpx.admin.service.OcpxUnifiedReportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OcpxReportControllerBindingTest {

	@Test
	void bindsBracketedMetricsFromTheFrontendQuery() throws Exception {
		OcpxUnifiedReportService reportService = mock(OcpxUnifiedReportService.class);
		when(reportService.query(any())).thenReturn(null);
		MockMvc mvc = mvc(reportService);

		mvc.perform(get("/ocpx/reports/hourly").param("grain", "DAY")
			.param("groupBy", "TIME")
			.param("mediaLevel", "ACCOUNT")
			.param("metrics[]", "impressions", "rawClicks")
			.param("current", "1")
			.param("size", "20")
			.param("startDate", "2026-08-05")
			.param("endDate", "2026-08-11"))
			.andExpect(status().isOk());

		ArgumentCaptor<UnifiedReportQuery> query = ArgumentCaptor.forClass(UnifiedReportQuery.class);
		verify(reportService).query(query.capture());
		assertThat(query.getValue().metrics()).containsExactly("impressions", "rawClicks");
	}

	@Test
	void allowsTheOptionalMetricsQueryToBeAbsent() throws Exception {
		OcpxUnifiedReportService reportService = mock(OcpxUnifiedReportService.class);
		when(reportService.query(any())).thenReturn(null);
		MockMvc mvc = mvc(reportService);

		mvc.perform(get("/ocpx/reports/hourly").param("grain", "DAY")
			.param("groupBy", "TIME")
			.param("mediaLevel", "ACCOUNT")
			.param("current", "1")
			.param("size", "20")
			.param("startDate", "2026-08-05")
			.param("endDate", "2026-08-11"))
			.andExpect(status().isOk());

		ArgumentCaptor<UnifiedReportQuery> query = ArgumentCaptor.forClass(UnifiedReportQuery.class);
		verify(reportService).query(query.capture());
		assertThat(query.getValue().metrics()).isNull();
	}

	private MockMvc mvc(OcpxUnifiedReportService reportService) {
		OcpxReportController controller = new OcpxReportController(mock(OcpxReportService.class),
				mock(OcpxDashboardService.class), reportService, mock(OcpxReportExportService.class));
		return MockMvcBuilders.standaloneSetup(controller).build();
	}

}
