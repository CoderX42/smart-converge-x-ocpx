

package com.yzytech.scx.pay.api.utils;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.yzytech.scx.pay.api.vo.PayConfigVO;

public class PayChannelContext {

	private static final ThreadLocal<PayConfigVO> THREAD_LOCAL = new TransmittableThreadLocal<>();

	public static void setChannel(PayConfigVO channel) {
		THREAD_LOCAL.set(channel);
	}

	public static PayConfigVO getChannel() {
		return THREAD_LOCAL.get();
	}

	public static void clear() {
		THREAD_LOCAL.remove();
	}

}
