package com.yzytech.scx.common.security.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.yzytech.scx.common.security.handler.BootGlobalExceptionHandlerResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SecurityAuditContextTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(BaseSecurityAuditListener.class, BootGlobalExceptionHandlerResolver.class);

	@Test
	void registersOneAuditPublisherAndTheExceptionHandler() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(BaseSecurityAuditListener.class);
			assertThat(context).hasSingleBean(BootGlobalExceptionHandlerResolver.class);
		});
	}

}
