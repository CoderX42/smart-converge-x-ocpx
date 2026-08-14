package com.yzytech.scx.ocpx.callback.web.aerim;

import com.yzytech.scx.ocpx.callback.web.CallbackContextRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** {@code /aerim/callback} 接收的 AERIM 转化参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AerimCallbackRequest extends CallbackContextRequest {

	private String action_type;

	private BigDecimal currency_amount;

}
