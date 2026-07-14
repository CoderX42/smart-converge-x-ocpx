

package com.yzytech.scx.common.log.config;

import com.yzytech.scx.common.log.aop.LogAopAspect;
import com.yzytech.scx.common.log.event.BootLogEventListener;
import com.yzytech.scx.system.api.remote.RemoteSysLogService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 日志配置
 *
 * @date 2022/6/10
 */
@Configuration
@RequiredArgsConstructor
public class BootSysLogConfig {

	@DubboReference
	private final RemoteSysLogService remoteSysLogService;

	@Bean
	public BootLogEventListener hxLogEventListener() {
		return new BootLogEventListener(remoteSysLogService);
	}

	@Bean
	public LogAopAspect logAopAspect(ApplicationEventPublisher publisher) {
		return new LogAopAspect(publisher);
	}

}
