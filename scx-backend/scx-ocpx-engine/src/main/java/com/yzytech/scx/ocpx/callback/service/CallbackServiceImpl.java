package com.yzytech.scx.ocpx.callback.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.delivery.DownstreamDeliveryDispatcher;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.fact.service.OcpxEventFactService;
import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Callback 核心处理实现。
 *
 * <p>
 * 处理顺序为：动作校验、MySQL 事实去重与广告主计费、Redis 原子扣量判定、MySQL 判定事实落库，最后仅对放行事件异步提交媒体回传。
 */
@Service
public class CallbackServiceImpl implements CallbackService {

	private static final DateTimeFormatter PURE_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final Logger impressionLog = LoggerFactory.getLogger("imp");

	private final StringRedisTemplate redis;

	private final Integer reportBucket;

	private final DeductionDecisionService deductionDecisionService;

	private final OcpxEventFactService eventFactService;

	private final OcpxFlowLogWriter flowLogWriter;

	public CallbackServiceImpl(StringRedisTemplate redis, @Value("${bid.job.thread}") Integer reportBucket,
			DeductionDecisionService deductionDecisionService, OcpxEventFactService eventFactService,
			OcpxFlowLogWriter flowLogWriter) {
		this.redis = redis;
		this.reportBucket = reportBucket;
		this.deductionDecisionService = deductionDecisionService;
		this.eventFactService = eventFactService;
		this.flowLogWriter = flowLogWriter;
	}

	@Override
	public CallbackHandleResult handleCallback(CallbackReq request) {
		try {
			return handleCallbackInternal(request);
		}
		catch (RuntimeException exception) {
			flowLogWriter.callback(request, false, "CALLBACK_PROCESSING_FAILED");
			throw exception;
		}
	}

	private CallbackHandleResult handleCallbackInternal(CallbackReq request) {
		impressionLog.info("callback reqId:{} action:{}", request.getFeedback().getReqId(), request.getActionType());
		if (request.getActionType() == null) {
			return complete(request, false, CallbackDecision.NOT_APPLICABLE);
		}
		OcpxEventFactService.CallbackFactResult factResult = eventFactService.recordCallback(request);
		if (!factResult.pendingDeduction()) {
			impressionLog.info("cb-->{}", "repeat " + request.getFeedback().getReqId() + "_" + request.getActionType());
			return complete(request, true, CallbackDecision.NOT_APPLICABLE);
		}

		if (factResult.firstEvent()) {
			// 兼容报表日志只记录首次事实；扣量审计和新报表不依赖该 Redis 队列。
			LogData logData = JSON.parseObject(JSON.toJSONString(request.getFeedback()), LogData.class);
			logData.setActionTime(request.getActionTime());
			logData.setPartnerActionType(request.getPartnerActionType());
			logData.setStandardActionType(request.getActionType().getIndex());
			logData.setCurrencyAmount(request.getCurrencyAmount());
			logData.setCallbackUrl("");
			logData.setTimeKeyRes(PURE_DATE_TIME.format(LocalDateTime.now()));
			setActionType(logData, request.getActionType());
			saveCallback(logData);
		}

		String lId = request.getFeedback().getLId();
		String mId = request.getFeedback().getMId();
		int typeIndex = request.getActionType().getIndex() - 1;
		DeductionDecisionService.Result deduction = deductionDecisionService.decide(factResult.factId(), lId, mId,
				typeIndex);
		boolean finalized = eventFactService.finalizeDeduction(factResult.factId(), deduction.ratio(),
				deduction.decision());
		if (!finalized) {
			return complete(request, true, CallbackDecision.NOT_APPLICABLE);
		}
		deductionDecisionService.complete(factResult.factId(), lId, mId);
		CallbackDecision decision = deduction.decision() == DeductionDecision.PASSED ? CallbackDecision.FORWARDED
				: CallbackDecision.DEDUCTED;
		if (decision == CallbackDecision.FORWARDED) {
			// FORWARDED 只表示已经提交异步投递，不表示媒体已经成功接收。
			DownstreamDeliveryDispatcher.dispatch(request);
		}
		impressionLog.info("media deduction reqId:{} action:{} decision:{} ratio:{} replay:{}",
				request.getFeedback().getReqId(), request.getActionType(), deduction.decision(), deduction.ratio(),
				deduction.replay());
		return complete(request, !factResult.firstEvent(), decision);
	}

	private CallbackHandleResult complete(CallbackReq request, boolean duplicate, CallbackDecision decision) {
		String message = duplicate && decision == CallbackDecision.NOT_APPLICABLE ? "CALLBACK_DUPLICATE"
				: switch (decision) {
					case FORWARDED -> "DOWNSTREAM_DELIVERY_SUBMITTED";
					case DEDUCTED -> "CALLBACK_DEDUCTED";
					case NOT_APPLICABLE -> "CALLBACK_NOT_APPLICABLE";
				};
		flowLogWriter.callback(request, duplicate || decision != CallbackDecision.NOT_APPLICABLE, message);
		return new CallbackHandleResult(duplicate, decision);
	}

	private void setActionType(LogData logData, ActionTypeEnum actionType) {
		switch (actionType) {
			case ACTIVATE -> logData.setActivate(1);
			case EVOKE -> logData.setEvoke(1);
			case LOGIN -> logData.setLogin(1);
			case REGISTER -> logData.setRegister(1);
			case PAYMENT -> logData.setPayment(1);
			case CUSTOMER_ACQUISITION -> logData.setCustomerAcquisition(1);
			case NEXT_STAY -> logData.setNextStay(1);
			case VISITS -> logData.setVisits(1);
			case AUTHENTICATION -> logData.setAuthentication(1);
			case ORDER -> logData.setPlaceOrder(1);
			case FINISH_THE_ORDER -> logData.setFinishTheOrder(1);
			case FIRST_ACTIVATION -> logData.setFirstActivation(1);
			case FIRST_ORDER -> logData.setFirstOrder(1);
			default -> {
			}
		}
	}

	private void saveCallback(LogData logData) {
		try {
			// 兼容日志队列失败不影响已经提交的事实与小时汇总。
			int index = (logData.hashCode() & Integer.MAX_VALUE) % reportBucket;
			redis.opsForList().rightPush(RedisConstants.DATA_REPORT_CALLBACK + index, JSON.toJSONString(logData));
		}
		catch (RuntimeException exception) {
			impressionLog.warn("callback compatibility queue failed reqId:{}", logData.getReqId(), exception);
		}
	}

}
