package com.yzytech.scx.adx.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdxMetricDictionaryTest {

	private final AdxMetricDictionary dictionary = new AdxMetricDictionary();

	@Test
	void lockedRatiosUseTheSharedDenominatorsAndZeroIsUnavailable() {
		Map<String, BigDecimal> facts = Map.of("validClicks", BigDecimal.valueOf(20), "activations",
				BigDecimal.valueOf(5), "registrations", BigDecimal.valueOf(2), "payments", BigDecimal.ONE);
		Map<String, com.yzytech.scx.adx.dto.UnifiedReportViews.MetricValue> values = dictionary.select(facts,
				Set.of("activationRate", "registrationRate", "paymentRate", "registrationPaymentRate"), true, false);
		assertThat(values.get("activationRate").value()).isEqualByComparingTo("25");
		assertThat(values.get("registrationRate").value()).isEqualByComparingTo("10");
		assertThat(values.get("paymentRate").value()).isEqualByComparingTo("20");
		assertThat(values.get("registrationPaymentRate").value()).isEqualByComparingTo("50");
		assertThat(dictionary.select(Map.of(), Set.of("activationRate"), false, false).get("activationRate").status())
			.isEqualTo("ZERO_DENOMINATOR");
	}

	@Test
	void dailyRegistrationPaymentRateIsNotInventedForOtherViews() {
		Map<String, com.yzytech.scx.adx.dto.UnifiedReportViews.MetricValue> values = dictionary.select(
				Map.of("registrations", BigDecimal.TEN, "payments", BigDecimal.ONE), Set.of("registrationPaymentRate"),
				false, false);
		assertThat(values.get("registrationPaymentRate").status()).isEqualTo("UNAVAILABLE");
		assertThat(dictionary.definitions(false, false))
			.anyMatch(definition -> "UNAVAILABLE".equals(definition.status()));
	}

}
