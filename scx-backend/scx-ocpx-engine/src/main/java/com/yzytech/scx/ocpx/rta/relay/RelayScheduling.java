package com.yzytech.scx.ocpx.rta.relay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Keep real-time deadlines independent of legacy retention and index jobs. */
@Configuration
public class RelayScheduling {

	@Bean(name = "taskScheduler")
	@Primary
	public ThreadPoolTaskScheduler legacyScheduler() {
		return scheduler("ocpx-scheduler-");
	}

	@Bean(name = "rtaScheduler")
	public ThreadPoolTaskScheduler rtaScheduler() {
		return scheduler("rta-scheduler-");
	}

	private ThreadPoolTaskScheduler scheduler(String name) {
		var scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix(name);
		scheduler.setRemoveOnCancelPolicy(true);
		return scheduler;
	}

}
