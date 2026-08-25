package com.yzytech.scx.ocpx.audit.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.audit.config.WireAuditProperties;
import com.yzytech.scx.ocpx.audit.model.WireAuditRecord;
import com.yzytech.scx.ocpx.audit.store.WireAuditStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WireAuditServiceTest {

	@Test
	void preservesRawQueryAndHeadersOnlyInsideAuthenticatedCiphertext() {
		WireAuditProperties properties = new WireAuditProperties();
		properties.setEnabled(true);
		OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("v1", Map.of("v1", "local-dev-key-01"), List.of("v1"));
		WireAuditStore store = mock(WireAuditStore.class);
		WireAuditService service = new WireAuditService(properties, crypto, store);
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("method", "GET");
		request.put("path", "/callback");
		request.put("rawQuery", "token=secret&a=1&a=2");
		request.put("headers", Map.of("authorization", List.of("Bearer secret")));

		WireAuditService.Handle handle = service.beginInbound("ADVERTISER_CALLBACK", "TEMPLATE_GET", "FORMAL",
				request, null);
		service.completeHttp(handle, 400, Map.of("x-result", List.of("failed")), "bad".getBytes(StandardCharsets.UTF_8),
				"CALLBACK_REJECTED", null, null);

		ArgumentCaptor<WireAuditRecord> captor = ArgumentCaptor.forClass(WireAuditRecord.class);
		verify(store, times(2)).persist(captor.capture());
		WireAuditRecord record = captor.getAllValues().get(1);
		assertThat(record.getResultStatus()).isEqualTo("FAILED");
		assertThat(record.getRequestPath()).isEqualTo("/callback");
		assertThat(record.toString()).doesNotContain("Bearer secret", "token=secret");
		byte[] clear = crypto.decrypt(new OcpxKeyRingCrypto.SealedValue(record.getKeyId(), record.getCryptoVersion(),
				record.getPayloadNonce(), record.getPayloadCiphertext()), OcpxKeyRingCrypto.PURPOSE_AUDIT,
				record.getPayloadAad().getBytes(StandardCharsets.UTF_8));
		assertThat(JSON.parseObject(clear).toJSONString()).contains("token=secret&a=1&a=2", "Bearer secret");
	}

}
