package com.yzytech.scx.ocpx.feedback.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.fact.service.OcpxEventFactService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.feedback.delivery.UpstreamDeliveryDispatcher;
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
 * 每条请求先由 MySQL 唯一事实完成去重、计费和小时汇总。事务成功后才进入兼容日志队列，并且只有首次有效、 允许投递的事件才会转发到广告主。
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

	private static final DateTimeFormatter PURE_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final Logger impressionLog = LoggerFactory.getLogger("imp");

	private final StringRedisTemplate redis;

	private final Integer reportBucket;

	private final OcpxEventFactService eventFactService;

	public FeedbackServiceImpl(StringRedisTemplate redis, @Value("${bid.job.thread}") Integer reportBucket,
			OcpxEventFactService eventFactService) {
		this.redis = redis;
		this.reportBucket = reportBucket;
		this.eventFactService = eventFactService;
	}

	@Override
	public FeedbackHandleResult handleFeedback(FeedbackReq request) {
		impressionLog.info("fb-->event:{} mId:{} tId:{} aId:{} lId:{} reqId:{} userType:{}", request.getEvent(),
				request.getMId(), request.getTId(), request.getAId(), request.getLId(), request.getReqId(),
				request.getUserType());
		request.setTimeKeyRes(PURE_DATE_TIME.format(LocalDateTime.now()));
		request.setFlagLog(ThreadLocalRandom.current().nextInt(10) == 1);
		OcpxEventFactService.FeedbackFactResult factResult = eventFactService.recordFeedback(request);
		boolean firstReport = factResult.firstEvent();
		if (firstReport) {
			switch (request.getEvent()) {
				case IMP -> {
					request.setShowSmt(1);
					request.setUniShowSmt(1);
				}
				case CLICK -> {
					request.setClkSmt(1);
					request.setUniClkSmt(1);
				}
				default -> {
				}
			}
		}
		impressionLog.info("fb-->{}", "spe0:" + request.getEvent().getKey() + ":" + request.getMId() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		if (firstReport) {
			saveFeedback(request);
		}
		impressionLog.info("fb-->{}", "spe1:" + request.getEvent().getKey() + ":" + request.getMId() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		if (factResult.forwardAllowed()) {
			UpstreamDeliveryDispatcher.dispatch(request);
		}
		impressionLog.info("fb-->{}", "spe2:" + request.getEvent().getKey() + ":" + request.getMId() + ":"
				+ request.getClkSmt() + ":" + request.getUniClkSmt());
		return new FeedbackHandleResult(firstReport);
	}

	private void saveFeedback(FeedbackReq request) {
		try {
			LogData logData = JSON.parseObject(JSON.toJSONString(request), LogData.class);
			// 兼容日志队列失败不回滚已经提交的事实、账务与汇总。
			int index = (request.hashCode() & Integer.MAX_VALUE) % reportBucket;
			redis.opsForList().rightPush(RedisConstants.DATA_REPORT + index, JSON.toJSONString(logData));
		}
		catch (RuntimeException exception) {
			impressionLog.warn("feedback compatibility queue failed reqId:{}", request.getReqId(), exception);
		}
	}

}
