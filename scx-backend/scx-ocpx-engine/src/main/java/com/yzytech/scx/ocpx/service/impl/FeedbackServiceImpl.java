package com.yzytech.scx.ocpx.service.impl;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.bean.LogData;
import com.yzytech.scx.ocpx.constant.RedisConstants;
import com.yzytech.scx.ocpx.constant.SystemConstants;
import com.yzytech.scx.ocpx.manager.RequestServiceManager;
import com.yzytech.scx.ocpx.service.result.FeedbackHandleResult;
import com.yzytech.scx.ocpx.service.wrapper.FeedbackService;
import com.yzytech.scx.ocpx.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

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
	public boolean handleRta(FeedbackReq request) {
		request.setTimeKeyRes(PURE_DATE_TIME.format(LocalDateTime.now()));
		request.setFlagLog(ThreadLocalRandom.current().nextInt(10) == 1);
		return RequestServiceManager.rta(request);
	}

	@Override
	public FeedbackHandleResult handleFeedback(FeedbackReq request) {
		impressionLog.info("fb-->{}", JSON.toJSONString(request));
		request.setTimeKeyRes(PURE_DATE_TIME.format(LocalDateTime.now()));
		request.setFlagLog(ThreadLocalRandom.current().nextInt(10) == 1);
		boolean firstReport = checkRepeatReport(request);
		impressionLog.info("fb-->{}", "spe0:" + request.getEvent().getKey() + ":" + request.getChid() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		saveFeedback(request);
		impressionLog.info("fb-->{}", "spe1:" + request.getEvent().getKey() + ":" + request.getChid() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		RequestServiceManager.feedback(request);
		impressionLog.info("fb-->{}", "spe2:" + request.getEvent().getKey() + ":" + request.getChid() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		return new FeedbackHandleResult(firstReport);
	}

	private void saveFeedback(FeedbackReq request) {
		LogData logData = JSON.parseObject(JSON.toJSONString(request), LogData.class);
		int index = (request.hashCode() & Integer.MAX_VALUE) % reportBucket;
		redis.opsForList().rightPush(RedisConstants.DATA_REPORT + index, JSON.toJSONString(logData));
	}

	private boolean checkRepeatReport(FeedbackReq request) {
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
