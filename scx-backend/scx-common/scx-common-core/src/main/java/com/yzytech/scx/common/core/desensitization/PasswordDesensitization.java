

package com.yzytech.scx.common.core.desensitization;

import com.yzytech.scx.common.core.config.AbstractDesensitization;

/**
 * 密码脱敏
 *
 * @date 2022/5/31
 */
public class PasswordDesensitization extends AbstractDesensitization {

	@Override
	public String serialize(String value) {
		return "******";
	}

}
