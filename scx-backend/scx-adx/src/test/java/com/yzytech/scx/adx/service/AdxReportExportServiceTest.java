package com.yzytech.scx.adx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.adx.config.AdxReportingProperties;
import com.yzytech.scx.adx.dto.UnifiedReportQuery;
import com.yzytech.scx.system.api.service.StorageUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdxReportExportServiceTest {

	private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxUnifiedReportService reportService = mock(AdxUnifiedReportService.class);

	private final AdxReportingProperties properties = new AdxReportingProperties();

	private final StorageUploadService storageUploadService = mock(StorageUploadService.class);

	private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

	private final AdxReportExportService service = new AdxReportExportService(jdbcTemplate, new ObjectMapper(),
			accessScope, reportService, properties, storageUploadService, transactionTemplate);

	@Test
	void disabledWorkerIsFailClosedAndDoesNotTouchStorage() {
		service.scheduledProcess();

		verifyNoInteractions(transactionTemplate, storageUploadService);
	}

	@Test
	@SuppressWarnings("unchecked")
	void emptyClaimDoesNotRenderOrUpload() {
		when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(null);

		assertThat(service.processNext()).isFalse();
		verifyNoInteractions(storageUploadService);
	}

	@Test
	void invalidExportRangeIsRejectedBeforeScopeOrStorageAccess() {
		UnifiedReportQuery query = new UnifiedReportQuery(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 8, 10), "DAY",
				"TASK", null, null, null, null, null, null, null, List.of("validClicks"), "dimension", "ASC", 1, 20);

		assertThatThrownBy(() -> service.createDeduction(query)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("跨度");
		verifyNoInteractions(reportService, storageUploadService);
	}

}
