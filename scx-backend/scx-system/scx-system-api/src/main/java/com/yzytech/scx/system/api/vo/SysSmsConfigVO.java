

package com.yzytech.scx.system.api.vo;

import com.yzytech.scx.common.sms.properties.SmsConfigDTO;
import lombok.Data;

/**
 * @description
 * @date 2025/7/2
 */

@Data
public class SysSmsConfigVO implements SmsConfigDTO {

	private String id;

	private String supplier;

	private String accessKeyId;

	private String accessKeySecret;

	private String signature;

	private String templateId;

	private String extraParams;

	private String name;

	private String smsCode;

	// 实现接口方法
	@Override
	public String getSmsCode() {
		return smsCode;
	}

	@Override
	public String getSupplier() {
		return supplier;
	}

	@Override
	public String getAccessKeyId() {
		return accessKeyId;
	}

	@Override
	public String getAccessKeySecret() {
		return accessKeySecret;
	}

	@Override
	public String getSignature() {
		return signature;
	}

	@Override
	public String getTemplateId() {
		return templateId;
	}

	@Override
	public String getExtraParams() {
		return extraParams;
	}

}
