package com.yzytech.scx.common.log.aop;

import static org.assertj.core.api.Assertions.assertThat;

import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.dto.SysLogReqDTO;
import org.junit.jupiter.api.Test;

class LogAopAspectTest {

	@Test
	void marksBusinessFailureWithoutPersistingResponseMessage() {
		SysLogReqDTO log = successfulLog();

		LogAopAspect.markBusinessResultFailure(log, Result.fail(404, "资源不存在"));

		assertThat(log.getStatus()).isEqualTo("0");
		assertThat(log.getExMsg()).isEqualTo("BUSINESS_RESULT:404");
		assertThat(log.getRequestParams()).isNull();
	}

	@Test
	void keepsSuccessfulResultSuccessful() {
		SysLogReqDTO log = successfulLog();

		LogAopAspect.markBusinessResultFailure(log, Result.success(true));

		assertThat(log.getStatus()).isEqualTo("1");
		assertThat(log.getExMsg()).isNull();
	}

	private static SysLogReqDTO successfulLog() {
		SysLogReqDTO log = new SysLogReqDTO();
		log.setStatus("1");
		return log;
	}

}
