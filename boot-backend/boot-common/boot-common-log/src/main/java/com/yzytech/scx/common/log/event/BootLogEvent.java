

package com.yzytech.scx.common.log.event;

import com.yzytech.scx.system.api.dto.SysLogReqDTO;
import com.yzytech.scx.system.api.entity.SysLog;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @date: 2023/6/26 11:51
 **/
public class BootLogEvent extends ApplicationEvent {

	@Getter
	private final SysLogReqDTO sysLog;

	public BootLogEvent(Object source, SysLogReqDTO sysLog) {
		super(source);
		this.sysLog = sysLog;
	}

}
