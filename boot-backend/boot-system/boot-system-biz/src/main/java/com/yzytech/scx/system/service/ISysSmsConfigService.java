

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysSmsConfig;
import com.yzytech.scx.system.api.enums.SmsCodeEnum;
import com.yzytech.scx.system.api.vo.SysSmsConfigVO;

/**
 * 短信配置
 *
 * @date 2025/7/02
 */
public interface ISysSmsConfigService extends IService<SysSmsConfig> {

	boolean updateSmsConfigById(SysSmsConfig sysSmsConfig);

	SysSmsConfigVO getSmsConfig(SmsCodeEnum smsCodeEnum);

	SysSmsConfigVO getDefaultConfig();

}
