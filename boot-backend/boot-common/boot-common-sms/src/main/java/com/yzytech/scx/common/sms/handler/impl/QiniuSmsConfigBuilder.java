

package com.yzytech.scx.common.sms.handler.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yzytech.scx.common.sms.handler.SmsConfigBuilderStrategy;
import com.yzytech.scx.common.sms.properties.SmsConfigDTO;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.sms4j.qiniu.config.QiNiuConfig;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @date 2025/7/2
 */
@Component
public class QiniuSmsConfigBuilder implements SmsConfigBuilderStrategy {

	private final static String SUPPLIER = "qiniu";

	@Override
	public boolean support(String supplier) {
		return SUPPLIER.equalsIgnoreCase(supplier);
	}

	@Override
	public void buildAndRegister(SmsConfigDTO config) {
		QiNiuConfig qiNiuConfig = new QiNiuConfig();
		BeanUtils.copyProperties(config, qiNiuConfig);
		qiNiuConfig.setConfigId(config.getSmsCode());
		if (StringUtils.hasText(config.getExtraParams())) {
			JSONObject jsonObject = JSONUtil.parseObj(config.getExtraParams());
			BeanUtil.copyProperties(jsonObject, qiNiuConfig);
		}
		SmsFactory.createSmsBlend(qiNiuConfig);
	}

}
