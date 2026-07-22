package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.delivery.entity.LinkEventMapping;
import com.yzytech.scx.delivery.mapper.LinkEventMappingMapper;
import com.yzytech.scx.tracking.dto.CallbackPayloadContext;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CallbackPayloadSnapshotServiceTest {

	@Test
	void retriesReuseImmutablePayloadWithoutAttemptNumber() {
		CallbackJobMapper callbackMapper = mock(CallbackJobMapper.class);
		LinkEventMappingMapper mappingMapper = mock(LinkEventMappingMapper.class);
		CallbackPayloadSnapshotService service = new CallbackPayloadSnapshotService(callbackMapper, mappingMapper,
				new ObjectMapper());
		CallbackJob job = new CallbackJob();
		job.setId("job-1");
		job.setAttributionResultId("attr-1");
		job.setActionType("ORIGINAL");
		job.setAttempts(3);
		CallbackPayloadContext context = new CallbackPayloadContext();
		context.setTaskCode("TASK-1");
		context.setLinkId("link-1");
		context.setLinkConfigVersion(2);
		context.setInternalEvent("ACTIVATE");
		context.setOccurredAt(LocalDateTime.of(2026, 7, 22, 12, 0));
		context.setClientId("scx_client");
		LinkEventMapping mapping = new LinkEventMapping();
		mapping.setExternalEvent("activate");
		when(callbackMapper.selectByIdForUpdate("job-1")).thenReturn(job);
		when(callbackMapper.selectPayloadContext("job-1")).thenReturn(context);
		when(mappingMapper.selectOne(any())).thenReturn(mapping);

		var first = service.snapshot("job-1");
		job.setAttempts(4);
		var retry = service.snapshot("job-1");

		assertThat(retry.payload()).isEqualTo(first.payload());
		assertThat(first.payload()).doesNotContain("attempt");
		assertThat(first.payloadHash()).hasSize(64);
		assertThat(first.clientId()).isEqualTo("scx_client");
		verify(mappingMapper, times(1)).selectOne(any());
	}

}
