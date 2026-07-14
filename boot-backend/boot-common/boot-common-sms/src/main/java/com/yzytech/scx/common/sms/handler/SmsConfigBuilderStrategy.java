

package com.yzytech.scx.common.sms.handler;

import com.yzytech.scx.common.sms.properties.SmsConfigDTO;

public interface SmsConfigBuilderStrategy {

	/**
	 * 判断是否支持该供应商
	 */
	boolean support(String supplier);

	/**
	 * 构建配置对象并注册 Blend
	 */
	void buildAndRegister(SmsConfigDTO config);

}
