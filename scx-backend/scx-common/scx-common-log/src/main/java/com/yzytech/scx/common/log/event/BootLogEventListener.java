

package com.yzytech.scx.common.log.event;

import com.yzytech.scx.system.api.dto.SysLogReqDTO;
import com.yzytech.scx.system.api.entity.SysLog;
import com.yzytech.scx.system.api.entity.SysLoginLog;
import com.yzytech.scx.system.api.remote.RemoteSysLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * @date: 2023/6/26 11:51
 **/
@Slf4j
@RequiredArgsConstructor
public class BootLogEventListener {

	private final RemoteSysLogService remoteSysLogService;

	// @Async("hxAsyncExecutor")
	@EventListener(BootLogEvent.class)
	public void saveSysLog(BootLogEvent event) {
		SysLogReqDTO sysLog = event.getSysLog();
		remoteSysLogService.saveLog(sysLog);
	}

	@Async("hxAsyncExecutor")
	@EventListener(BootLoginLogEvent.class)
	public void saveSysLoginLog(BootLoginLogEvent event) {
		SysLoginLog sysLoginLog = event.getSysLoginLog();
		remoteSysLogService.saveLoginLog(sysLoginLog);
	}

}
