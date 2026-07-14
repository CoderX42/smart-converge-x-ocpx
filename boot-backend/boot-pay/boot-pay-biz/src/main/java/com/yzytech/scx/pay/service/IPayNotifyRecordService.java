

package com.yzytech.scx.pay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.pay.api.entity.PayNotifyRecord;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付订单
 *
 * @date 2022/6/16
 */
public interface IPayNotifyRecordService extends IService<PayNotifyRecord> {

	/**
	 * 微信支付通知
	 * @param terminalType
	 * @param notifyData
	 * @return
	 */
	String wxPayNotify(String terminalType, String notifyData);

	/**
	 * 支付宝支付通知
	 * @param terminalType
	 * @param request
	 * @return
	 */
	String aliPayNotify(String terminalType, HttpServletRequest request);

	/**
	 * 微信退款通知
	 * @param notifyData
	 * @return
	 */
	String wxPayRefundNotify(String terminalType, String notifyData);

	/**
	 * 微信服务商支付通知
	 * @param terminalType
	 * @param notifyData
	 * @return
	 */
	String wxPartnerPayNotify(String terminalType, String notifyData);

}
