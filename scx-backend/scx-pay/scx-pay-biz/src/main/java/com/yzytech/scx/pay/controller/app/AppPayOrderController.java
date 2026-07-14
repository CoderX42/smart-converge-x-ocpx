

package com.yzytech.scx.pay.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.pay.api.entity.PayTradeOrder;
import com.yzytech.scx.pay.service.IPayTradeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/app/order")
@RequiredArgsConstructor
@Tag(description = "order", name = "支付订单")
public class AppPayOrderController {

	private final IPayTradeOrderService payTradeOrderService;

	@Operation(summary = "通过订单号查询")
	@GetMapping("/{orderNo}")
	public Result getOrder(@PathVariable("orderNo") String orderNo) {
		return Result.success(payTradeOrderService
			.getOne(Wrappers.<PayTradeOrder>lambdaQuery().eq(PayTradeOrder::getOutTradeNo, orderNo)));
	}

}
