package com.yzytech.scx.ocpx.callback.web.kimi;

import com.yzytech.scx.ocpx.callback.web.CallbackContextRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * KIMI 广告主 {@code /kimi/callback} 的转化参数（SGYZ kimi 协议迁移）。
 *
 * <p>
 * {@code ext}/{@code s} 来自 query，{@code action_type}/{@code action_time} 来自 JSON body， 为原协议的 query+body 混合绑定，迁移时原样保留。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KimiCallbackReq extends CallbackContextRequest {

	/**
	 * 转化动作： 1 激活 2 新登 3 唤醒 4 次日回访 5 下单 6 购买 7 首唤 8 首购。
	 */
	private String action_type;

	/** 转化时间，毫秒。 */
	private Long action_time;

}
