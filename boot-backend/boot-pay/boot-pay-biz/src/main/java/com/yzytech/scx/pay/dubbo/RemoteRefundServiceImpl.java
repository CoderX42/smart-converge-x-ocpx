

package com.yzytech.scx.pay.dubbo;

import com.yzytech.scx.pay.api.dto.CreateRefundsReqDTO;
import com.yzytech.scx.pay.api.remote.RemoteRefundService;
import com.yzytech.scx.pay.handler.PayRefundOrderHandler;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @description
 * @date 2024/11/23
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteRefundServiceImpl implements RemoteRefundService {

	private final Map<String, PayRefundOrderHandler> payRefundOrderHandlerMap;

	@Override
	public Object refunds(CreateRefundsReqDTO createRefundsReqDTO) {
		return payRefundOrderHandlerMap.get(createRefundsReqDTO.getRefundType()).refund(createRefundsReqDTO);
	}

}
