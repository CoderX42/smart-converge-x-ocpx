

package com.yzytech.scx.system.dubbo;

import com.yzytech.scx.system.api.dto.SysLogReqDTO;
import com.yzytech.scx.system.api.entity.SysLog;
import com.yzytech.scx.system.api.entity.SysLoginLog;
import com.yzytech.scx.system.api.remote.RemoteSysLogService;
import com.yzytech.scx.system.service.ISysLogService;
import com.yzytech.scx.system.service.ISysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * @description
 * @date 2024/11/22
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSysLogServiceImpl implements RemoteSysLogService {

	private final ISysLogService sysLogService;

	private final ISysLoginLogService sysLoginLogService;

	@Override
	public boolean saveLog(SysLogReqDTO sysLogReqDTO) {
		SysLog sysLog = new SysLog();
		BeanUtils.copyProperties(sysLogReqDTO, sysLog);
		sysLogService.save(sysLog);
		return true;
	}

	@Override
	public boolean saveLoginLog(SysLoginLog sysLoginLog) {
		return sysLoginLogService.save(sysLoginLog);
	}

}
