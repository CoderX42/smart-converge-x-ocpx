

package com.yzytech.scx.common.core.entity;

import lombok.Data;

@Data
public class OpenAuthSuccessEvent {

	private String appId;

	private String appName;

	private String codeUrl;

	// 1.小程序；2.公众号
	private String authType;// 试用小程序：0.否；1.是；
	private String betaStatus;

}
