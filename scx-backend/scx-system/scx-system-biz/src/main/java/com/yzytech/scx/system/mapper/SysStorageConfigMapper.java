

package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import com.yzytech.scx.system.api.entity.SysStorageConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件存储配置
 *
 * @since 2022/2/26 16:50
 */
@Mapper
public interface SysStorageConfigMapper extends BaseMapper<SysStorageConfig> {

	/**
	 * 查询文件配置
	 *
	 * @date 2022/9/20
	 * @return: com.yzytech.scx.system.common.dto.SysStorageConfigDTO
	 */
	SysStorageConfigDTO selectConfig();

}
