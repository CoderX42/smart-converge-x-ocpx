

package com.yzytech.scx.common.log.event;

import com.yzytech.scx.system.api.entity.SysLoginLog;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @date: 2023/6/26 11:51
 **/
public class BootLoginLogEvent extends ApplicationEvent {

	@Getter
	private final SysLoginLog sysLoginLog;

	public BootLoginLogEvent(Object source, SysLoginLog sysLoginLog) {
		super(source);
		this.sysLoginLog = sysLoginLog;
	}

}
