package com.yzytech.scx.ocpx.task;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.yzytech.scx.ocpx.bean.LogData;
import com.yzytech.scx.ocpx.constant.RedisConstants;
import com.yzytech.scx.ocpx.service.wrapper.ReportService;
import com.yzytech.scx.ocpx.util.IpUtil;
import io.lettuce.core.RedisCommandTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Async
@Slf4j
@Component
public class StorageTask {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private static final Logger otherLog = LoggerFactory.getLogger("other");

	private final StringRedisTemplate redis;

	private final ReportService reportService;

	private final Integer reportBucket;

	public StorageTask(StringRedisTemplate redis, ReportService reportService,
			@Value("${bid.job.thread}") Integer reportBucket) {
		this.redis = redis;
		this.reportService = reportService;
		this.reportBucket = reportBucket;
	}

	@Scheduled(cron = "${app.jobs.storage-cron:0 0/1 * * * ?}")
	public void consumeFeedback() {
		String tempKey = "";
		String lockKey = "rtb{consumeFeedback";
		String inet4Address = IpUtil.getInet4Address();
		Integer slot = null;
		for (int index = 0; index < reportBucket; index++) {
			tempKey = lockKey + index + "}";
			Boolean check = redis.opsForValue().setIfAbsent(tempKey, inet4Address, 10, TimeUnit.MINUTES);
			if (!ObjectUtils.isNotEmpty(check) || check) {
				slot = index;
				break;
			}
		}
		if (slot == null) {
			otherLog.info("consumeFeedback  notGetLock");
			return;
		}
		otherLog.info("consumeFeedback begin{}", slot);
		try {
			while (true) {
				try {
					Long count = redis.opsForList().size(RedisConstants.DATA_REPORT + slot);
					if (count == null || count < 1) {
						break;
					}
					List<String> jsons = redis.opsForList().range(RedisConstants.DATA_REPORT + slot, 0, 1000);
					if (CollectionUtils.isEmpty(jsons)) {
						return;
					}
					List<LogData> data = new ArrayList<>(jsons.size());
					jsons.forEach(json -> {
						if (json.contains("\\u") || json.contains("\\x")) {
							return;
						}
						LogData item = JSON.parseObject(json, LogData.class);
						data.add(item);
					});
					if (!CollectionUtils.isEmpty(data)) {
						try {
							reportService.addFeedback(data);
							redis.opsForList().trim(RedisConstants.DATA_REPORT + slot, jsons.size(), -1);
						}
						catch (Exception exception) {
							errorLog.error("consumeFeedback：addFeedback error:{},data:{}", exception.getMessage(),
									JSON.toJSONString(data), exception);
						}
					}
				}
				catch (RedisCommandTimeoutException exception) {
					errorLog.error("consumeFeedback：{},redis error:{}", slot, exception.getMessage(), exception);
				}
			}
		}
		catch (Exception exception) {
			errorLog.error("consumeFeedback：error:{}", exception.getMessage(), exception);
		}
		finally {
			if (StringUtils.hasText(tempKey)) {
				redis.delete(tempKey);
			}
		}
	}

	@Scheduled(cron = "${app.jobs.storage-cron:0 0/1 * * * ?}")
	public void consumeCallback() {
		String tempKey = "";
		String lockKey = "rtb{consumeCallback";
		String inet4Address = IpUtil.getInet4Address();
		Integer slot = null;
		for (int index = 0; index < reportBucket; index++) {
			tempKey = lockKey + index + "}";
			Boolean check = redis.opsForValue().setIfAbsent(tempKey, inet4Address, 10, TimeUnit.MINUTES);
			if (!ObjectUtils.isNotEmpty(check) || check) {
				slot = index;
				break;
			}
		}
		if (slot == null) {
			otherLog.info("consumeCallback notGetLock");
			return;
		}
		otherLog.info("consumeCallback begin{}", slot);
		try {
			while (true) {
				try {
					Long count = redis.opsForList().size(RedisConstants.DATA_REPORT_CALLBACK + slot);
					if (count == null || count < 1) {
						break;
					}
					List<String> jsons = redis.opsForList().range(RedisConstants.DATA_REPORT_CALLBACK + slot, 0, 1000);
					if (CollectionUtils.isEmpty(jsons)) {
						return;
					}
					List<LogData> data = new ArrayList<>(jsons.size());
					jsons.forEach(json -> {
						if (json.contains("\\u") || json.contains("\\x")) {
							return;
						}
						LogData item = JSON.parseObject(json, LogData.class);
						data.add(item);
					});
					if (!CollectionUtils.isEmpty(data)) {
						try {
							reportService.addCallback(data);
							redis.opsForList().trim(RedisConstants.DATA_REPORT_CALLBACK + slot, jsons.size(), -1);
						}
						catch (Exception exception) {
							errorLog.error("consumeCallback：addFeedback error:{},data:{}", exception.getMessage(),
									JSON.toJSONString(data), exception);
						}
					}
				}
				catch (RedisCommandTimeoutException exception) {
					errorLog.error("consumeCallback：{},redis error:{}", slot, exception.getMessage(), exception);
				}
			}
		}
		catch (Exception exception) {
			errorLog.error("consumeCallback：error:{}", exception.getMessage(), exception);
		}
		finally {
			if (StringUtils.hasText(tempKey)) {
				redis.delete(tempKey);
			}
		}
	}

}
