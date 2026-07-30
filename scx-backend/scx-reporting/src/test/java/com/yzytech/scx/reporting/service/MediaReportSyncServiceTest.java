package com.yzytech.scx.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.campaign.entity.MediaPublishingBinding;
import com.yzytech.scx.campaign.mapper.AdGroupMapper;
import com.yzytech.scx.campaign.mapper.MediaPublishingBindingMapper;
import com.yzytech.scx.campaign.mapper.OptimizationGoalMapper;
import com.yzytech.scx.inventory.adapter.MediaAdapter;
import com.yzytech.scx.inventory.adapter.MediaAdapterRegistry;
import com.yzytech.scx.inventory.dto.CapabilityView;
import com.yzytech.scx.inventory.dto.ReportPullResult;
import com.yzytech.scx.inventory.entity.MediaAccount;
import com.yzytech.scx.inventory.mapper.MediaAccountMapper;
import com.yzytech.scx.reporting.entity.MediaReportHourlyFact;
import com.yzytech.scx.reporting.mapper.MediaReportHourlyFactMapper;
import com.yzytech.scx.reporting.mapper.MediaReportSyncRunMapper;
import com.yzytech.scx.reporting.mapper.ReportReconciliationIssueMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MediaReportSyncServiceTest {

	private final MediaAccountMapper accountMapper = mock(MediaAccountMapper.class);

	private final MediaPublishingBindingMapper bindingMapper = mock(MediaPublishingBindingMapper.class);

	private final AdGroupMapper adGroupMapper = mock(AdGroupMapper.class);

	private final OptimizationGoalMapper optimizationGoalMapper = mock(OptimizationGoalMapper.class);

	private final MediaAdapterRegistry registry = mock(MediaAdapterRegistry.class);

	private final MediaReportSyncRunMapper runMapper = mock(MediaReportSyncRunMapper.class);

	private final MediaReportHourlyFactMapper factMapper = mock(MediaReportHourlyFactMapper.class);

	private final ReportReconciliationIssueMapper issueMapper = mock(ReportReconciliationIssueMapper.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final MediaAdapter adapter = mock(MediaAdapter.class);

	private final MediaReportSyncService service = new MediaReportSyncService(accountMapper, bindingMapper,
			adGroupMapper, optimizationGoalMapper, registry, runMapper, factMapper, issueMapper, scopeService);

	@BeforeEach
	void setUp() {
		MediaAccount account = new MediaAccount();
		account.setId("account-1");
		account.setMediaId("media-1");
		account.setCurrency("CNY");
		account.setTimezone("Asia/Shanghai");
		when(accountMapper.selectById("account-1")).thenReturn(account);
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(registry.resolve("account-1")).thenReturn(adapter);
		when(adapter.getCapabilities())
			.thenReturn(new CapabilityView("TEST", List.of(), List.of(), List.of(), false, false, true));
	}

	@Test
	void rejectsCursorThatDoesNotAdvance() {
		ReportPullResult page = new ReportPullResult("same", true, List.of(), LocalDateTime.now());
		when(adapter.pullReport(anyString(), anyString(), anyString(), any())).thenReturn(page);

		assertThatThrownBy(() -> service.sync("account-1", LocalDateTime.of(2026, 7, 30, 10, 0),
				LocalDateTime.of(2026, 7, 30, 11, 0)))
			.hasMessage("媒体适配器游标未前进");
	}

	@Test
	void unknownExternalCampaignIsPersistedAsVersionedUnboundFact() {
		ReportPullResult.ReportRow row = new ReportPullResult.ReportRow("unknown-campaign",
				LocalDateTime.of(2026, 7, 30, 10, 0), 100, 12, 3, new BigDecimal("88.20"), null);
		when(adapter.pullReport(anyString(), anyString(), anyString(), any()))
			.thenReturn(new ReportPullResult(null, false, List.of(row), LocalDateTime.now()));
		when(adapter.pullBilling(anyString(), anyString(), anyString(), any()))
			.thenReturn(new ReportPullResult(null, false, List.of(), LocalDateTime.now()));

		service.sync("account-1", LocalDateTime.of(2026, 7, 30, 10, 0), LocalDateTime.of(2026, 7, 30, 11, 0));

		ArgumentCaptor<MediaReportHourlyFact> fact = ArgumentCaptor.forClass(MediaReportHourlyFact.class);
		verify(factMapper).insert(fact.capture());
		assertThat(fact.getValue().getQualityCode()).isEqualTo("UNBOUND");
		assertThat(fact.getValue().getTrackingTaskId()).isNull();
		assertThat(fact.getValue().getSourceVersion()).isEqualTo(1);
		assertThat(fact.getValue().getSpendAmount()).isEqualByComparingTo("88.20");
	}

	@Test
	void sourceCurrencyMismatchIsPreservedButMarkedIncomplete() {
		MediaPublishingBinding binding = new MediaPublishingBinding();
		binding.setId("binding-1");
		binding.setTrackingTaskId("task-1");
		when(bindingMapper.selectOne(any())).thenReturn(binding);
		ReportPullResult.ReportRow row = new ReportPullResult.ReportRow("campaign-1",
				LocalDateTime.of(2026, 7, 30, 10, 0), 100, 12, 3, new BigDecimal("88.20"), null, "USD");
		when(adapter.pullReport(anyString(), anyString(), anyString(), any()))
			.thenReturn(new ReportPullResult(null, false, List.of(row), LocalDateTime.now()));
		when(adapter.pullBilling(anyString(), anyString(), anyString(), any()))
			.thenReturn(new ReportPullResult(null, false, List.of(), LocalDateTime.now()));

		service.sync("account-1", LocalDateTime.of(2026, 7, 30, 10, 0), LocalDateTime.of(2026, 7, 30, 11, 0));

		ArgumentCaptor<MediaReportHourlyFact> fact = ArgumentCaptor.forClass(MediaReportHourlyFact.class);
		verify(factMapper).insert(fact.capture());
		assertThat(fact.getValue().getQualityCode()).isEqualTo("CURRENCY_MISMATCH");
		assertThat(fact.getValue().getCurrency()).isEqualTo("USD");
	}

	@Test
	void boundCampaignWithoutOptimizationGoalIsMarkedIncomplete() {
		MediaPublishingBinding binding = new MediaPublishingBinding();
		binding.setId("binding-1");
		binding.setTrackingTaskId("task-1");
		when(bindingMapper.selectOne(any())).thenReturn(binding);
		ReportPullResult.ReportRow row = new ReportPullResult.ReportRow("campaign-1",
				LocalDateTime.of(2026, 7, 30, 10, 0), 100, 12, 3, new BigDecimal("88.20"), null, "CNY");
		when(adapter.pullReport(anyString(), anyString(), anyString(), any()))
			.thenReturn(new ReportPullResult(null, false, List.of(row), LocalDateTime.now()));
		when(adapter.pullBilling(anyString(), anyString(), anyString(), any()))
			.thenReturn(new ReportPullResult(null, false, List.of(), LocalDateTime.now()));

		service.sync("account-1", LocalDateTime.of(2026, 7, 30, 10, 0), LocalDateTime.of(2026, 7, 30, 11, 0));

		ArgumentCaptor<MediaReportHourlyFact> fact = ArgumentCaptor.forClass(MediaReportHourlyFact.class);
		verify(factMapper).insert(fact.capture());
		assertThat(fact.getValue().getQualityCode()).isEqualTo("MISSING_GOAL");
	}

}
