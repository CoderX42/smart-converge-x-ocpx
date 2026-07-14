

package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.system.api.entity.SysConfig;
import com.yzytech.scx.system.mapper.SysConfigMapper;
import com.yzytech.scx.system.service.ISysConfigService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 全局系统配置表
 *
 * @date 2025/7/01
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements ISysConfigService {

	@Override
	public String getConfigValue(String key) {
		SysConfig sysConfig = this.getOne(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
		if (Objects.nonNull(sysConfig)) {
			return sysConfig.getConfigValue();
		}
		return null;
	}

}
