

package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.system.api.entity.SysLoginLog;
import com.yzytech.scx.system.mapper.SysLoginLogMapper;
import com.yzytech.scx.system.service.ISysLoginLogService;
import org.springframework.stereotype.Service;

/**
 * 登录日志
 *
 * @since 2022/2/26 16:47
 */
@Service
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog> implements ISysLoginLogService {

}
