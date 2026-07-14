

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysConfig;

/**
 * 全局系统配置表
 *
 * @date 2025/7/01
 */
public interface ISysConfigService extends IService<SysConfig> {

	/**
	 * get config value by key
	 * @param key
	 * @return
	 */
	String getConfigValue(String key);

}
