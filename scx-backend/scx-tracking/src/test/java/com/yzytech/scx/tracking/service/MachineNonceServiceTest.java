package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.entity.MachineNonce;
import com.yzytech.scx.tracking.mapper.MachineNonceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MachineNonceServiceTest {

	private final MachineNonceMapper mapper = mock(MachineNonceMapper.class);

	private final MachineNonceService service = new MachineNonceService(mapper);

	@Test
	void nonceCanOnlyBeClaimedOnce() {
		when(mapper.insert(any(MachineNonce.class))).thenReturn(1).thenThrow(new DuplicateKeyException("duplicate"));

		assertThat(service.claim("nonce-12345678")).isTrue();
		assertThat(service.claim("nonce-12345678")).isFalse();
	}

	@Test
	void nonceLengthIsBounded() {
		assertThat(service.claim("short")).isFalse();
		assertThat(service.claim("x".repeat(129))).isFalse();
	}

}
