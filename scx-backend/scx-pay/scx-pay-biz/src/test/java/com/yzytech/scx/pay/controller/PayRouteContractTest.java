package com.yzytech.scx.pay.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class PayRouteContractTest {

	@Test
	void supportsCanonicalAndLegacyConsoleRoutesForPaymentResources() {
		assertRoutes(PayOrderController.class, "/pay", "/pay/pay");
		assertRoutes(PayNotifyRecordController.class, "/notify", "/pay/notify");
		assertRoutes(PayRefundOrderController.class, "/refundorder", "/pay/refundorder");
		assertRoutes(PayConfigController.class, "/payconfig", "/pay/payconfig");
	}

	private void assertRoutes(Class<?> controller, String... expectedRoutes) {
		RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
		assertThat(mapping).isNotNull();
		assertThat(Arrays.asList(mapping.value())).containsExactlyInAnyOrder(expectedRoutes);
	}

}
