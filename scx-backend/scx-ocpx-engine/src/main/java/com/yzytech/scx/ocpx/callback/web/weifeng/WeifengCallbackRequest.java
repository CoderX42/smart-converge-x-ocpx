package com.yzytech.scx.ocpx.callback.web.weifeng;

import com.yzytech.scx.ocpx.callback.web.CallbackContextRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** {@code /weifeng/callback} 接收的微风互动转化参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WeifengCallbackRequest extends CallbackContextRequest {

	private String event;

	/** 非付费事件可能原样返回 {@code __AMOUNT__}，因此先按字符串绑定。 */
	private String amount;

	/** 非留存事件可能原样返回 {@code __DAYS__}，因此先按字符串绑定。 */
	private String days;

}
