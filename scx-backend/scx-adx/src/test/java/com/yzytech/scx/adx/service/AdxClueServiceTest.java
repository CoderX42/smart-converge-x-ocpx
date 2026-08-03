package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.dto.CatalogRequests;
import com.yzytech.scx.adx.mapper.AdxClueMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdxClueServiceTest {

	private final AdxClueMapper mapper = mock(AdxClueMapper.class);

	private final AdxClueService service = new AdxClueService(mapper);

	@Test
	void rejectsUnknownStatus() {
		CatalogRequests.SaveClue request = new CatalogRequests.SaveClue(1, 9, null, "13800138000", null, null, null,
				null, null, null, null, null, null, null);

		assertThatThrownBy(() -> service.save(null, request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("线索状态无效");
	}

	@Test
	void pendingSubmitStaysBlockedWithoutExternalCredentials() {
		when(mapper.selectCount(any())).thenReturn(3L);

		Map<String, Object> result = service.submitPending();

		assertThat(result).containsEntry("status", "BLOCKED").containsEntry("pending", 3L);
		assertThat(result.get("message").toString()).contains("任务凭据");
	}

	@Test
	void updatesSelectedClues() {
		when(mapper.update(any(), any())).thenReturn(2);
		CatalogRequests.BatchClueStatus request = new CatalogRequests.BatchClueStatus(1, List.of("1", "2"), null, null,
				null, null, null, null, null);

		assertThat(service.batchStatus(request)).isEqualTo(2);
	}

	@Test
	void updatesCluesUsingBoundedFilter() {
		when(mapper.update(any(), any())).thenReturn(5);
		CatalogRequests.BatchClueStatus request = new CatalogRequests.BatchClueStatus(2, List.of(), "FILTER", 0,
				LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), "张", "138", 1);

		assertThat(service.batchStatus(request)).isEqualTo(5);
	}

	@Test
	void rejectsUnboundedFilterUpdate() {
		CatalogRequests.BatchClueStatus request = new CatalogRequests.BatchClueStatus(2, List.of(), "FILTER", 0, null,
				null, null, null, null);

		assertThatThrownBy(() -> service.batchStatus(request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("请选择线索或提供原状态和日期范围");
	}

}
