

package com.yzytech.scx.common.sms.handler;

import com.yzytech.scx.common.sms.properties.SmsConfigDTO;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @date 2025/7/2
 */
@Component
public class SmsConfigBuilderFactory {

	private final List<SmsConfigBuilderStrategy> strategies;

	@Autowired
	public SmsConfigBuilderFactory(List<SmsConfigBuilderStrategy> strategies) {
		this.strategies = strategies;
	}

	public SmsBlend build(SmsConfigDTO config) {
		for (SmsConfigBuilderStrategy strategy : strategies) {
			if (strategy.support(config.getSupplier())) {
				strategy.buildAndRegister(config);
				SmsBlend smsBlend = SmsFactory.getSmsBlend(config.getSmsCode());
				if (smsBlend != null) {
					return smsBlend;
				}
				else {
					throw new RuntimeException("短信服务构建失败: " + config.getSupplier());
				}
			}
		}
		throw new RuntimeException("不支持的短信供应商: " + config.getSupplier());
	}

	public void unregister(String configId) {
		SmsFactory.unregister(configId);
	}

}
