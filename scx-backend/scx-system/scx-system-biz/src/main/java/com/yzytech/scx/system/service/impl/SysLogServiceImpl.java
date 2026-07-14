

package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.system.api.entity.SysLog;
import com.yzytech.scx.system.mapper.SysLogMapper;
import com.yzytech.scx.system.service.ISysLogService;
import org.springframework.stereotype.Service;

/**
 * 操作日志
 *
 * @since 2022/2/26 16:47
 */
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements ISysLogService {

}
