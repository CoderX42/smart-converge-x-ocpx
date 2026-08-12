package com.yzytech.scx.ocpx.service.impl;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.bean.LogData;
import com.yzytech.scx.ocpx.constant.RedisConstants;
import com.yzytech.scx.ocpx.constant.SystemConstants;
import com.yzytech.scx.ocpx.constant.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.controller.ChannelServiceManager;
import com.yzytech.scx.ocpx.service.helper.RedisHelper;
import com.yzytech.scx.ocpx.service.result.CallbackDecision;
import com.yzytech.scx.ocpx.service.result.CallbackHandleResult;
import com.yzytech.scx.ocpx.service.wrapper.CallbackService;
import com.yzytech.scx.ocpx.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CallbackServiceImpl implements CallbackService {

	private static final DateTimeFormatter PURE_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final Logger impressionLog = LoggerFactory.getLogger("imp");

	private final StringRedisTemplate redis;

	private final Integer reportBucket;

	private final RedisHelper redisHelper;

	public CallbackServiceImpl(StringRedisTemplate redis, @Value("${bid.job.thread}") Integer reportBucket,
			RedisHelper redisHelper) {
		this.redis = redis;
		this.reportBucket = reportBucket;
		this.redisHelper = redisHelper;
	}

	@Override
	public CallbackHandleResult handleFeedback(CallbackReq request) {
		impressionLog.info("cb-->{}", JSON.toJSONString(request));
		if (request.getActionType() == null) {
			return new CallbackHandleResult(false, CallbackDecision.NOT_APPLICABLE);
		}
		Boolean first = redis.opsForHash()
			.putIfAbsent(
					RedisConstants.REPEAT_CALLBACK_REPORT + StringUtil.hashModulo(request.getFeedback().getReqId(),
							SystemConstants.REPEAT_REPORT_BUCKET),
					request.getFeedback().getReqId() + "_" + request.getActionType(), "1");
		if (!first) {
			impressionLog.info("cb-->{}", "repeat " + request.getFeedback().getReqId() + "_" + request.getActionType());
			return new CallbackHandleResult(true, CallbackDecision.NOT_APPLICABLE);
		}

		LogData logData = JSON.parseObject(JSON.toJSONString(request.getFeedback()), LogData.class);
		logData.setActionTime(request.getActionTime());
		logData.setCallbackUrl("");
		logData.setTimeKeyRes(PURE_DATE_TIME.format(LocalDateTime.now()));
		setActionType(logData, request.getActionType());
		saveCallback(logData);

		String advertiserId = request.getFeedback().getLinkid();
		String channelId = request.getFeedback().getChid();
		int typeIndex = request.getActionType().getIndex() - 1;
		double radio = redisHelper.getAdvertisingRadio(advertiserId, channelId);
		int[] original = redisHelper.getAdvertisingOriginal(advertiserId, channelId);
		int[] fixed = redisHelper.getAdvertisingFixed(advertiserId, channelId);
		int increment = 0;
		CallbackDecision decision = CallbackDecision.DEDUCTED;
		if (radio < 1.0F) {
			float a = fixed[typeIndex] * 1.0F / (original[typeIndex] + 1);
			float b = (fixed[typeIndex] + 1) * 1.0F / (original[typeIndex] + 1);
			if (b <= radio) {
				ChannelServiceManager.callback(request);
				increment++;
				decision = CallbackDecision.FORWARDED;
				impressionLog.info("cb-->{}", "skip keel:" + fixed[typeIndex] + "/" + original[typeIndex] + "="
						+ (fixed[typeIndex] * 1.0F / original[typeIndex]) + "~" + radio);
			}
			else {
				impressionLog.info("cb-->{}", "skip jump:" + fixed[typeIndex] + "/" + original[typeIndex] + "="
						+ (fixed[typeIndex] * 1.0F / original[typeIndex]) + "~" + radio);
			}
		}
		else {
			ChannelServiceManager.callback(request);
			increment++;
			decision = CallbackDecision.FORWARDED;
			float a = (fixed[typeIndex] + 1) * 1.0F / (original[typeIndex] + 1);
			float b = (fixed[typeIndex] + 2) * 1.0F / (original[typeIndex] + 1);
			if ((a + b) / 2 < radio) {
				impressionLog.info("cb-->{}", "skip add:" + fixed[typeIndex] + "/" + original[typeIndex] + "="
						+ (fixed[typeIndex] * 1.0F / original[typeIndex]) + "~" + radio);
			}
			else {
				impressionLog.info("cb-->{}", "skip keep:" + fixed[typeIndex] + "/" + original[typeIndex] + "="
						+ (fixed[typeIndex] * 1.0F / original[typeIndex]) + "~" + radio);
			}
		}
		original[typeIndex]++;
		redisHelper.setAdvertisingOriginal(advertiserId, channelId, original);
		if (increment != 0) {
			fixed[typeIndex] += increment;
			redisHelper.setAdvertisingFixed(advertiserId, channelId, fixed);
		}
		return new CallbackHandleResult(false, decision);
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
			default -> {
			}
		}
	}

	private void saveCallback(LogData logData) {
		int index = (logData.hashCode() & Integer.MAX_VALUE) % reportBucket;
		redis.opsForList().rightPush(RedisConstants.DATA_REPORT_CALLBACK + index, JSON.toJSONString(logData));
	}

}
