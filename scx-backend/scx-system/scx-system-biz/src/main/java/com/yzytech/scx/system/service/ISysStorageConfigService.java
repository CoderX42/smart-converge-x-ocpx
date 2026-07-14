

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import com.yzytech.scx.system.api.entity.SysStorageConfig;

/**
 * 文件存储配置
 *
 * @since 2022/2/26 16:47
 */
public interface ISysStorageConfigService extends IService<SysStorageConfig> {

	/**
	 * 查询文件配置
	 *
	 * @date 2022/9/20
	 * @return: com.yzytech.scx.system.common.dto.SysStorageConfigDTO
	 */
	SysStorageConfigDTO getConfig();

	/**
	 * 新增配置
	 * @param sysStorageConfig
	 * @return
	 */
	boolean saveStorageConfig(SysStorageConfig sysStorageConfig);

	/**
	 * 修改配置
	 * @param sysStorageConfig
	 * @return
	 */
	boolean updateStorageConfigById(SysStorageConfig sysStorageConfig);

}
