
package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.system.api.entity.SysAdLaunch;
import com.yzytech.scx.system.mapper.SysAdLaunchMapper;
import com.yzytech.scx.system.service.ISysAdLaunchService;
import org.springframework.stereotype.Service;

/**
 * 启动广告配置
 *
 * @date 2025/6/14
 */
@Service
public class SysAdLaunchServiceImpl extends ServiceImpl<SysAdLaunchMapper, SysAdLaunch> implements ISysAdLaunchService {

	@Override
	public SysAdLaunch getConfig() {
		return baseMapper.selectConfig();
	}

	@Override
	public SysAdLaunch getActiveConfig() {
		return baseMapper.selectActiveConfig();
	}

}
