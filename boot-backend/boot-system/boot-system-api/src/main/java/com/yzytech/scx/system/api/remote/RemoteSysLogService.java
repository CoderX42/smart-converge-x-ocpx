

package com.yzytech.scx.system.api.remote;

import com.yzytech.scx.system.api.dto.SysLogReqDTO;
import com.yzytech.scx.system.api.entity.SysLog;
import com.yzytech.scx.system.api.entity.SysLoginLog;

/**
 * 系统操作日志
 *
 * @date: 2023/6/26 11:01
 **/
public interface RemoteSysLogService {

	/**
	 * 保存系统操作日志
	 * @param sysLogReqDTO
	 * @date 2022/6/28
	 * @return: com.yzytech.scx.common.core.util.Result
	 */
	boolean saveLog(SysLogReqDTO sysLogReqDTO);

	/**
	 * 保存登录日志
	 * @param sysLoginLog
	 * @date 2022/6/28
	 * @return: com.yzytech.scx.common.core.util.Result
	 */
	boolean saveLoginLog(SysLoginLog sysLoginLog);

}
