

package com.yzytech.scx.common.security.handler;

import com.yzytech.scx.common.core.constant.CommonConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义异常
 *
 * @date 2022/9/20
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BootBusinessException extends RuntimeException {

	private Integer code;

	private String msg;

	public BootBusinessException() {
		super("业务异常");
		this.code = CommonConstants.FAIL;
		this.msg = "业务异常";
	}

	public BootBusinessException(String msg) {
		super(msg); // 关键：传递给 RuntimeException
		this.code = CommonConstants.FAIL;
		this.msg = msg;
	}

	public BootBusinessException(Integer code, String msg) {
		super(msg); // 关键：传递给 RuntimeException
		this.code = code;
		this.msg = msg;
	}

}
