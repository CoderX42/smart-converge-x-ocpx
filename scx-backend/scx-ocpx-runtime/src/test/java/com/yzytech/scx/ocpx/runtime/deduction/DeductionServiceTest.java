package com.yzytech.scx.ocpx.runtime.deduction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;

class DeductionServiceTest {

	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

	private final OcpxPersistenceRepository repository = mock(OcpxPersistenceRepository.class);

	private final DeductionService service = new DeductionService(redis, repository);

	@Test
	void ratioBoundsHaveDeterministicBehaviorWithoutRedis() {
		when(repository.findDeductionDecision("full")).thenReturn(Optional.empty());
		when(repository.findDeductionDecision("zero")).thenReturn(Optional.empty());

		assertThat(service.decide("full", route(BigDecimal.ONE), OcpxEventType.ACTIVATE).forwarded()).isTrue();
		assertThat(service.decide("zero", route(BigDecimal.ZERO), OcpxEventType.ACTIVATE).forwarded()).isFalse();
	}

	@Test
	void alwaysReusesPersistedDecision() {
		when(repository.findDeductionDecision("existing")).thenReturn(Optional.of(false));

		DeductionService.Decision decision = service.decide("existing", route(BigDecimal.ONE), OcpxEventType.REGISTER);

		assertThat(decision.forwarded()).isFalse();
		assertThat(decision.reason()).isEqualTo("EXISTING_DECISION");
	}

	private RouteSnapshot route(BigDecimal ratio) {
		return new RouteSnapshot("route-1", "biz-1", 3, "channel-1", "demo", "STANDARD", "HMAC", 1, "secret",
				"advertiser-1", "app-1", "COCO", null, "https://coco.example/click", null,
				"https://downstream.example/callback", BigDecimal.ONE, ratio, "OFF", "OFF");
	}

}
