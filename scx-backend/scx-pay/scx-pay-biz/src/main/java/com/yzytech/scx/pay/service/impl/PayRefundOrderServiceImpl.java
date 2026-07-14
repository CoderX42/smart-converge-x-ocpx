

package com.yzytech.scx.pay.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.pay.api.entity.PayRefundOrder;
import com.yzytech.scx.pay.mapper.PayRefundOrderMapper;
import com.yzytech.scx.pay.service.IPayRefundOrderService;
import org.springframework.stereotype.Service;

@Service
public class PayRefundOrderServiceImpl extends ServiceImpl<PayRefundOrderMapper, PayRefundOrder>
		implements IPayRefundOrderService {

}
