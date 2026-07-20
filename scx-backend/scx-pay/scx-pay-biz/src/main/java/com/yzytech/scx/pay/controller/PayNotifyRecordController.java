

package com.yzytech.scx.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Response;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.pay.api.constants.PayConstants;
import com.yzytech.scx.pay.api.entity.PayNotifyRecord;
import com.yzytech.scx.pay.service.IPayNotifyRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping({ "/notify", "/pay/notify" })
@RequiredArgsConstructor
@Tag(description = "notify", name = "支付回调")
public class PayNotifyRecordController {

	private final IPayNotifyRecordService payNotifyRecordService;

	@Operation(summary = "微信支付回调通知")
	@PostMapping("/wx/{terminalType}")
	public String notifyOrderWx(@PathVariable("terminalType") String terminalType, @RequestBody String notifyData) {
		log.info("微信支付回调通知: {}", notifyData);
return payNotifyRecordService.wxPayNotify(terminalType, notifyData);
	}

	@Operation(summary = "微信服务商支付回调通知")
	@PostMapping("/partner-notify/wx/{terminalType}")
	public String notifyOrderPartnerWx(@PathVariable("terminalType") String terminalType, @RequestBody String notifyData) {
return payNotifyRecordService.wxPartnerPayNotify(terminalType, notifyData);
	}

	@Operation(summary = "支付宝支付回调")
	@PostMapping("/alipay/{terminalType}")
	public String notifyOrderAli(@PathVariable("terminalType") String terminalType, HttpServletRequest request) {
return payNotifyRecordService.aliPayNotify(terminalType, request);
	}

	@Operation(summary = "微信退款回调通知")
	@PostMapping("/refunds/wx/{terminalType}")
	public String notifyRefundOrderWx(@PathVariable("terminalType") String terminalType, @RequestBody String notifyData) {
		log.info("微信退款回调通知: {}", notifyData);
return payNotifyRecordService.wxPayRefundNotify(terminalType, notifyData);
	}

	@Operation(summary = "通过id查询")
	@SaCheckPermission("pay:paynotifyrecord:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(payNotifyRecordService.getById(id));
	}

	@Operation(summary = "分页查询")
	@SaCheckPermission("pay:paynotifyrecord:page")
	@GetMapping("/page")
	public Result getPage(Page page, PayNotifyRecord payNotifyRecord) {
		return Result.success(payNotifyRecordService.page(page, Wrappers.query(payNotifyRecord)));
	}

}
