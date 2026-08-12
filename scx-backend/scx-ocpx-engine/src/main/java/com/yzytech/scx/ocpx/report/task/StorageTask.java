package com.yzytech.scx.ocpx.report.task;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.report.service.ReportService;
import com.yzytech.scx.ocpx.shared.util.IpUtil;
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

/**
 * Redis Feedback/Callback 队列的定时落库消费者。
 *
 * <p>
 * 每个实例尝试占用一个分桶锁，按最多 1001 条的旧批次范围读取 List。只有对应批次的全部数据库写入 成功后才裁剪队列。当前实现没有 processing
 * 队列、事务、重试、DLQ 或锁所有者校验，维护时不可误认为 已具备可靠消息语义。
 */
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

	/** 每分钟尝试消费一个 Feedback 分桶。 */
	@Scheduled(cron = "${app.jobs.storage-cron:0 0/1 * * * ?}")
	public void consumeFeedback() {
		String tempKey = "";
		String lockKey = "rtb{consumeFeedback";
		String inet4Address = IpUtil.getInet4Address();
		Integer slot = null;
		// 从第一个可加锁分桶开始，本次调度只消费一个分桶。
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
			// 持锁期间循环清空当前分桶，锁的固定有效期为十分钟。
			while (true) {
				try {
					Long count = redis.opsForList().size(RedisConstants.DATA_REPORT + slot);
					if (count == null || count < 1) {
						break;
					}
					// Redis LRANGE 两端均包含，因此 0..1000 最多返回 1001 条。
					List<String> jsons = redis.opsForList().range(RedisConstants.DATA_REPORT + slot, 0, 1000);
					if (CollectionUtils.isEmpty(jsons)) {
						return;
					}
					List<LogData> data = new ArrayList<>(jsons.size());
					jsons.forEach(json -> {
						// 兼容旧数据清洗：带反斜杠 Unicode/十六进制转义的记录不落库，并随原批次裁剪。
						if (json.contains("\\u") || json.contains("\\x")) {
							return;
						}
						LogData item = JSON.parseObject(json, LogData.class);
						data.add(item);
					});
					if (!CollectionUtils.isEmpty(data)) {
						try {
							reportService.addFeedback(data);
							// 数据库写入全部成功后才从 List 头部确认本次原始读取数量。
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
				// 保留旧行为：直接删除锁键，不校验当前锁值是否仍属于本实例。
				redis.delete(tempKey);
			}
		}
	}

	/** 每分钟尝试消费一个 Callback 分桶。 */
	@Scheduled(cron = "${app.jobs.storage-cron:0 0/1 * * * ?}")
	public void consumeCallback() {
		String tempKey = "";
		String lockKey = "rtb{consumeCallback";
		String inet4Address = IpUtil.getInet4Address();
		Integer slot = null;
		// Callback 与 Feedback 使用独立锁和队列，但遵循相同的单分桶消费方式。
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
					// Redis LRANGE 两端均包含，因此 0..1000 最多返回 1001 条。
					List<String> jsons = redis.opsForList().range(RedisConstants.DATA_REPORT_CALLBACK + slot, 0, 1000);
					if (CollectionUtils.isEmpty(jsons)) {
						return;
					}
					List<LogData> data = new ArrayList<>(jsons.size());
					jsons.forEach(json -> {
						// 兼容旧数据清洗：带反斜杠 Unicode/十六进制转义的记录不落库，并随原批次裁剪。
						if (json.contains("\\u") || json.contains("\\x")) {
							return;
						}
						LogData item = JSON.parseObject(json, LogData.class);
						data.add(item);
					});
					if (!CollectionUtils.isEmpty(data)) {
						try {
							reportService.addCallback(data);
							// 数据库写入全部成功后才从 List 头部确认本次原始读取数量。
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
				// 保留旧行为：直接删除锁键，不校验当前锁值是否仍属于本实例。
				redis.delete(tempKey);
			}
		}
	}

}
