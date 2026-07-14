

package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.common.core.desensitization.KeyDesensitization;
import com.yzytech.scx.common.sms.handler.SmsConfigBuilderFactory;
import com.yzytech.scx.system.api.entity.SysSmsConfig;
import com.yzytech.scx.system.api.enums.SmsCodeEnum;
import com.yzytech.scx.system.api.vo.SysSmsConfigVO;
import com.yzytech.scx.system.mapper.SysSmsConfigMapper;
import com.yzytech.scx.system.service.ISysSmsConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Objects;

/**
 * 短信配置
 *
 * @date 2025/7/02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSmsConfigServiceImpl extends ServiceImpl<SysSmsConfigMapper, SysSmsConfig>
		implements ISysSmsConfigService {

	private final SmsConfigBuilderFactory smsConfigBuilderFactory;

	private final KeyDesensitization keyDesensitization = new KeyDesensitization();

	@Override
	public boolean save(SysSmsConfig sysSmsConfig) {
		try {
			smsConfigBuilderFactory.unregister(sysSmsConfig.getSmsCode());
		}
		catch (Exception e) {
			log.error("短信配置更新失败：{}", e.getMessage());
		}
		return baseMapper.insert(sysSmsConfig) > 0;
	}

	@Override
	public boolean updateSmsConfigById(SysSmsConfig sysSmsConfig) {
		SysSmsConfig target = this.getById(sysSmsConfig.getId());
		if (Objects.isNull(target)) {
			throw new RuntimeException("短信配置不存在");
		}
		if (StringUtils.hasText(target.getAccessKeyId())
				&& keyDesensitization.serialize(target.getAccessKeyId()).equals(sysSmsConfig.getAccessKeyId())) {
			sysSmsConfig.setAccessKeyId(null);
		}
		if (StringUtils.hasText(target.getAccessKeySecret())
				&& keyDesensitization.serialize(target.getAccessKeySecret())
					.equals(sysSmsConfig.getAccessKeySecret())) {
			sysSmsConfig.setAccessKeySecret(null);
		}
		try {
			smsConfigBuilderFactory.unregister(target.getSmsCode());
		}
		catch (Exception e) {
			log.error("短信配置更新失败：{}", e.getMessage());
		}
		return this.updateById(sysSmsConfig);
	}

	@Override
	public boolean removeById(Serializable id) {
		SysSmsConfig target = this.getById(id);
		if (Objects.isNull(target)) {
			throw new RuntimeException("短信配置不存在");
		}
		try {
			smsConfigBuilderFactory.unregister(target.getSmsCode());
		}
		catch (Exception e) {
			log.error("短信配置更新失败：{}", e.getMessage());
		}
		return baseMapper.deleteById(id) > 0;
	}

	@Override
	public SysSmsConfigVO getSmsConfig(SmsCodeEnum smsCodeEnum) {
		return baseMapper.getSmsConfigByCode(smsCodeEnum.getCode());
	}

	@Override
	public SysSmsConfigVO getDefaultConfig() {
		return baseMapper.selectDefaultConfig();
	}

}
