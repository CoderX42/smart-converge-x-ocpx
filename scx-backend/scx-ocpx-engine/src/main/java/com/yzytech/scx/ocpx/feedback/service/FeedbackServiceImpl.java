package com.yzytech.scx.ocpx.feedback.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.shared.constant.SystemConstants;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryDispatcher;
import com.yzytech.scx.ocpx.shared.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Feedback 核心处理实现。
 *
 * <p>
 * 每条请求先设置接收时间和抽样标记，再用 Redis Hash 计算去重指标，随后无论是否重复都写入报表队列 并异步转发。这一“重复仍转发”的行为是当前兼容契约。
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

	private static final DateTimeFormatter PURE_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final Logger impressionLog = LoggerFactory.getLogger("imp");

	private final StringRedisTemplate redis;

	private final Integer reportBucket;

	public FeedbackServiceImpl(StringRedisTemplate redis, @Value("${bid.job.thread}") Integer reportBucket) {
		this.redis = redis;
		this.reportBucket = reportBucket;
	}

	@Override
	public FeedbackHandleResult handleFeedback(FeedbackReq request) {
		impressionLog.info("fb-->{}", JSON.toJSONString(request));
		request.setTimeKeyRes(PURE_DATE_TIME.format(LocalDateTime.now()));
		request.setFlagLog(ThreadLocalRandom.current().nextInt(10) == 1);
		boolean firstReport = checkRepeatReport(request);
		impressionLog.info("fb-->{}", "spe0:" + request.getEvent().getKey() + ":" + request.getMId() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		saveFeedback(request);
		impressionLog.info("fb-->{}", "spe1:" + request.getEvent().getKey() + ":" + request.getMId() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		// 去重只改变 uniShowSmt/uniClkSmt；重复事件仍进入上游转发链路。
		UpstreamDeliveryDispatcher.dispatch(request);
		impressionLog.info("fb-->{}", "spe2:" + request.getEvent().getKey() + ":" + request.getMId() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		return new FeedbackHandleResult(firstReport);
	}

	private void saveFeedback(FeedbackReq request) {
		LogData logData = JSON.parseObject(JSON.toJSONString(request), LogData.class);
		// 按对象哈希分散到配置数量的 Redis List，由 StorageTask 分槽消费。
		int index = (request.hashCode() & Integer.MAX_VALUE) % reportBucket;
		redis.opsForList().rightPush(RedisConstants.DATA_REPORT + index, JSON.toJSONString(logData));
	}

	private boolean checkRepeatReport(FeedbackReq request) {
		// 总量指标对每次请求加一；去重指标仅在 Redis HSETNX 成功时加一。
		switch (request.getEvent()) {
			case IMP -> request.setShowSmt(1);
			case CLICK -> request.setClkSmt(1);
			default -> {
			}
		}
		Boolean first = redis.opsForHash()
			.putIfAbsent(
					RedisConstants.REPEAT_REPORT
							+ StringUtil.hashModulo(request.getReqId(), SystemConstants.REPEAT_REPORT_BUCKET),
					request.getReqId() + request.getEvent().getKey(), "1");
		if (first) {
			switch (request.getEvent()) {
				case IMP -> request.setUniShowSmt(1);
				case CLICK -> request.setUniClkSmt(1);
				default -> {
				}
			}
		}
		return first;
	}

}
