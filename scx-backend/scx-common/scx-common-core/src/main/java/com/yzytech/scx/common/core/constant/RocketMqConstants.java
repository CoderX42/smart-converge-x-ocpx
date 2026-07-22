
package com.yzytech.scx.common.core.constant;

/**
 * RocketMq常量
 *
 * @date 2022/8/6
 */
public interface RocketMqConstants {

	/** 订单取消 topic */
	String ORDER_CANCEL_TOPIC = "order-cancel-topic";

	/** 支付通知topic */
	String PAY_NOTIFY_TOPIC = "pay-notify-topic";

	/** 系统支付通知topic */
	String SYS_PAY_NOTIFY_TOPIC = "sscx-pay-notify-topic";

	/** 订单支付通知topic */
	String ORDER_PAY_SUCCESS_NOTIFY_TOPIC = "order-pay-success-notify-topic";

	/** 订单退款通知topic */
	String ORDER_REFUND_SUCCESS_NOTIFY_TOPIC = "order-refund-success-notify-topic";

	/** 订单完成通知topic */
	String ORDER_COMPLETE_NOTIFY_TOPIC = "order-complete-notify-topic";

	/** 余额充值支付通知topic */
	String RECHARGE_PAY_NOTIFY_TOPIC = "recharge-pay-notify-topic";

	/** 拼团成团通知topic */
	String GROUPON_SUCCESS_TOPIC = "groupon-success-notify-topic";

	/** 拼团过期通知topic */
	String GROUPON_EXPIRE_TOPIC = "groupon-expire-notify-topic";

	/** 商品库存变动通知topic */
	String PRODUCT_STOCK_TOPIC = "product-stock-topic";

	/** 退款通知topic */
	String PAY_REFUND_NOTIFY_TOPIC = "pay-refund-notify-topic";

	/** 充值退款通知topic */
	String RECHARGE_PAY_REFUND_NOTIFY_TOPIC = "recharge-pay-refund-notify-topic";

	/** 开放平台-授权成功通知topic */
	String WX_OPEN_AUTHORIZED_SUCCESS = "wx-open-authorized-success";

	/** 开放平台-授权取消通知topic */
	String WX_OPEN_UNAUTHORIZED_SUCCESS = "wx-open-unauthorized-success";

	/** 店铺余额解冻 topic */
	String SHOP_BALANCE_TOPIC = "shop-balance-topic";

	/** 支付通知group */
	String PAY_GROUP = "pay-group";

	/** 发送消息超时时间 */
	long TIME_OUT = 3000;

	/** 订单超时取消等级 30分钟 */
	int ORDER_CANCEL_LEVEL = 16;

}
