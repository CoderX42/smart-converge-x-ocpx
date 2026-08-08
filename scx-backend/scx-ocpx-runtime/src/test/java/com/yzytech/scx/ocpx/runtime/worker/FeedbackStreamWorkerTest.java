package com.yzytech.scx.ocpx.runtime.worker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackStreamWorkerTest {

	@Test
	void recognizesBusyGroupInsideSpringDataWrapper() {
		RuntimeException redisBusy = new RuntimeException("BUSYGROUP Consumer Group name already exists");
		RuntimeException springDataWrapper = new RuntimeException("Error in execution", redisBusy);

		assertThat(FeedbackStreamWorker.isBusyGroup(springDataWrapper)).isTrue();
		assertThat(FeedbackStreamWorker.isBusyGroup(new RuntimeException("Redis unavailable"))).isFalse();
	}

}
