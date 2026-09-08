
package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysAdLaunch;

/**
 * 启动广告配置
 *
 * @date 2025/6/14
 */
public interface ISysAdLaunchService extends IService<SysAdLaunch> {

	SysAdLaunch getConfig();

	SysAdLaunch getActiveConfig();

}
