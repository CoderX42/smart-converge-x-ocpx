package com.yzytech.scx.ocpx.callback.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.delivery.DownstreamDeliveryDispatcher;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.shared.constant.SystemConstants;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.redis.RedisHelper;
import com.yzytech.scx.ocpx.shared.util.StringUtil;
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
 * 处理顺序为：动作校验、Redis 去重、报表队列记录、按链接与媒体维度执行扣量计算、异步回传媒体， 最后更新原始量和回传量计数。Redis 数组读改写保持历史非原子行为。
 */
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
	public CallbackHandleResult handleCallback(CallbackReq request) {
		impressionLog.info("callback reqId:{} action:{}", request.getFeedback().getReqId(), request.getActionType());
		if (request.getActionType() == null) {
			return new CallbackHandleResult(false, CallbackDecision.NOT_APPLICABLE);
		}
		// 同一请求 ID 与动作类型只处理一次，避免重复写报表和重复回传。
		Boolean first = redis.opsForHash()
			.putIfAbsent(
					RedisConstants.REPEAT_CALLBACK_REPORT + StringUtil.hashModulo(request.getFeedback().getReqId(),
							SystemConstants.REPEAT_REPORT_BUCKET),
					request.getFeedback().getReqId() + "_" + request.getActionType(), "1");
		if (!first) {
			impressionLog.info("cb-->{}", "repeat " + request.getFeedback().getReqId() + "_" + request.getActionType());
			return new CallbackHandleResult(true, CallbackDecision.NOT_APPLICABLE);
		}

		// Callback 报表复用 Feedback 维度，并将本次动作映射为对应的 0/1 指标列。
		LogData logData = JSON.parseObject(JSON.toJSONString(request.getFeedback()), LogData.class);
		logData.setActionTime(request.getActionTime());
		logData.setPartnerActionType(request.getPartnerActionType());
		logData.setStandardActionType(request.getActionType().getIndex());
		logData.setCurrencyAmount(request.getCurrencyAmount());
		logData.setCallbackUrl("");
		logData.setTimeKeyRes(PURE_DATE_TIME.format(LocalDateTime.now()));
		setActionType(logData, request.getActionType());
		saveCallback(logData);

		String lId = request.getFeedback().getLId();
		String mId = request.getFeedback().getMId();
		// 19 位数组覆盖全部已编号动作；RedisHelper 会把历史 11 位状态自动补零。
		int typeIndex = request.getActionType().getIndex() - 1;
		double ratio = redisHelper.getRouteRatio(lId, mId);
		int[] originalCounts = redisHelper.getRouteOriginalCounts(lId, mId);
		int[] deliveredCounts = redisHelper.getRouteDeliveredCounts(lId, mId);
		int increment = 0;
		CallbackDecision decision = CallbackDecision.DEDUCTED;
		if (ratio < 1.0F) {
			// 计算本次回传后的累计比例；只有该比例不超过目标值时才放行。
			float a = deliveredCounts[typeIndex] * 1.0F / (originalCounts[typeIndex] + 1);
			float b = (deliveredCounts[typeIndex] + 1) * 1.0F / (originalCounts[typeIndex] + 1);
			if (b <= ratio) {
				DownstreamDeliveryDispatcher.dispatch(request);
				increment++;
				decision = CallbackDecision.FORWARDED;
				impressionLog.info("cb-->{}",
						"skip keel:" + deliveredCounts[typeIndex] + "/" + originalCounts[typeIndex] + "="
								+ (deliveredCounts[typeIndex] * 1.0F / originalCounts[typeIndex]) + "~" + ratio);
			}
			else {
				impressionLog.info("cb-->{}",
						"skip jump:" + deliveredCounts[typeIndex] + "/" + originalCounts[typeIndex] + "="
								+ (deliveredCounts[typeIndex] * 1.0F / originalCounts[typeIndex]) + "~" + ratio);
			}
		}
		else {
			DownstreamDeliveryDispatcher.dispatch(request);
			increment++;
			decision = CallbackDecision.FORWARDED;
			float a = (deliveredCounts[typeIndex] + 1) * 1.0F / (originalCounts[typeIndex] + 1);
			float b = (deliveredCounts[typeIndex] + 2) * 1.0F / (originalCounts[typeIndex] + 1);
			if ((a + b) / 2 < ratio) {
				impressionLog.info("cb-->{}", "skip add:" + deliveredCounts[typeIndex] + "/" + originalCounts[typeIndex]
						+ "=" + (deliveredCounts[typeIndex] * 1.0F / originalCounts[typeIndex]) + "~" + ratio);
			}
			else {
				impressionLog.info("cb-->{}",
						"skip keep:" + deliveredCounts[typeIndex] + "/" + originalCounts[typeIndex] + "="
								+ (deliveredCounts[typeIndex] * 1.0F / originalCounts[typeIndex]) + "~" + ratio);
			}
		}
		// 先记录全部上游原始转化，再仅对实际下游回传事件增加计数。
		originalCounts[typeIndex]++;
		redisHelper.setRouteOriginalCounts(lId, mId, originalCounts);
		if (increment != 0) {
			deliveredCounts[typeIndex] += increment;
			redisHelper.setRouteDeliveredCounts(lId, mId, deliveredCounts);
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
			case FIRST_ACTIVATION -> logData.setFirstActivation(1);
			case FIRST_ORDER -> logData.setFirstOrder(1);
			default -> {
			}
		}
	}

	private void saveCallback(LogData logData) {
		// 按对象哈希分散到配置数量的 Redis List，由 StorageTask 分槽消费。
		int index = (logData.hashCode() & Integer.MAX_VALUE) % reportBucket;
		redis.opsForList().rightPush(RedisConstants.DATA_REPORT_CALLBACK + index, JSON.toJSONString(logData));
	}

}
