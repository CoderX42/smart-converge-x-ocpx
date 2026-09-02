package com.yzytech.scx.ocpx.report.task;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.report.service.ReportService;
import com.yzytech.scx.ocpx.shared.util.IpUtil;
import io.lettuce.core.RedisCommandTimeoutException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis Feedback/Callback 队列的定时落库消费者。
 *
 * <p>
 * 每个实例尝试占用一个分桶锁，按有限批次读取 List。只有对应批次的全部数据库写入成功后才裁剪队列；失败批次保留在队列中，等待下一次调度。
 */
@Async
@Slf4j
@Component
@ConditionalOnProperty(name = "ocpx.compatibility-report.enabled", havingValue = "true")
public class StorageTask {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private static final Logger otherLog = LoggerFactory.getLogger("other");

	private final StringRedisTemplate redis;

	private final ReportService reportService;

	private final Integer reportBucket;

	private final int batchSize;

	private final int maxBatchRetries;

	private final long retryBackoffMs;

	private MeterRegistry meterRegistry;

	public StorageTask(StringRedisTemplate redis, ReportService reportService, Integer reportBucket) {
		this(redis, reportService, reportBucket, 500, 3, 1000L);
	}

	@Autowired
	public StorageTask(StringRedisTemplate redis, ReportService reportService,
			@Value("${bid.job.thread}") Integer reportBucket,
			@Value("${app.jobs.storage-batch-size:500}") Integer batchSize,
			@Value("${app.jobs.storage-max-retries:3}") Integer maxBatchRetries,
			@Value("${app.jobs.storage-retry-backoff-ms:1000}") Long retryBackoffMs) {
		this.redis = redis;
		this.reportService = reportService;
		this.reportBucket = reportBucket;
		this.batchSize = Math.max(1, batchSize == null ? 500 : batchSize);
		this.maxBatchRetries = Math.max(0, maxBatchRetries == null ? 3 : maxBatchRetries);
		this.retryBackoffMs = Math.max(0, retryBackoffMs == null ? 1000L : retryBackoffMs);
	}

	@Autowired(required = false)
	void setMeterRegistry(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	/** 每分钟尝试消费一个 Feedback 分桶。 */
	@Scheduled(cron = "${app.jobs.storage-cron:0 0/1 * * * ?}")
	public void consumeFeedback() {
		consume(RedisConstants.DATA_REPORT, "rtb{consumeFeedback", reportService::addFeedback, "consumeFeedback");
	}

	/** 每分钟尝试消费一个 Callback 分桶。 */
	@Scheduled(cron = "${app.jobs.storage-cron:0 0/1 * * * ?}")
	public void consumeCallback() {
		consume(RedisConstants.DATA_REPORT_CALLBACK, "rtb{consumeCallback", reportService::addCallback,
				"consumeCallback");
	}

	private void consume(String queuePrefix, String lockPrefix, Consumer<List<LogData>> writer, String operation) {
		String tempKey = "";
		String lockKey = lockPrefix;
		String inet4Address = IpUtil.getInet4Address();
		Integer slot = null;
		// Callback 与 Feedback 使用独立锁和队列，但遵循相同的单分桶消费方式。
		try {
			for (int index = 0; index < reportBucket; index++) {
				tempKey = lockKey + index + "}";
				Boolean check = redis.opsForValue().setIfAbsent(tempKey, inet4Address, 10, TimeUnit.MINUTES);
				if (Boolean.TRUE.equals(check)) {
					slot = index;
					break;
				}
			}
		}
		catch (RedisCommandTimeoutException exception) {
			errorLog.error("{} redis lock timeout slot:{} size:{} cause:{}", operation, tempKey, batchSize,
				summarize(exception));
			return;
		}
		catch (RuntimeException exception) {
			errorLog.error("{} lock acquisition failed slot:{} size:{} cause:{}", operation, tempKey, batchSize,
				summarize(exception));
			return;
		}
		if (slot == null) {
			otherLog.info("{} notGetLock", operation);
			return;
		}
		otherLog.info("{} begin{}", operation, slot);
		int consecutiveFailures = 0;
		try {
			while (true) {
				try {
					String queueKey = queuePrefix + slot;
					Long count = redis.opsForList().size(queueKey);
					if (count == null || count < 1) {
						break;
					}
					recordDepth(operation, count);
					// Redis LRANGE 两端均包含，因此 batchSize - 1 是本批最后一个下标。
					List<String> jsons = redis.opsForList().range(queueKey, 0, batchSize - 1);
					if (CollectionUtils.isEmpty(jsons)) {
						break;
					}
					List<LogData> data = new ArrayList<>(jsons.size());
					int skipped = 0;
					for (String json : jsons) {
						// 兼容旧数据清洗：带反斜杠 Unicode/十六进制转义的记录不落库，并随原批次裁剪。
						if (!StringUtils.hasText(json) || json.contains("\\u") || json.contains("\\x")) {
							skipped++;
							continue;
						}
						try {
							LogData item = JSON.parseObject(json, LogData.class);
							if (item == null) {
								skipped++;
							}
							else {
								data.add(item);
							}
						}
						catch (RuntimeException exception) {
							skipped++;
						}
					}
					if (CollectionUtils.isEmpty(data)) {
						redis.opsForList().trim(queueKey, jsons.size(), -1);
						otherLog.warn("{} skipped entire batch slot:{} size:{}", operation, slot, skipped);
						increment("ocpx.storage.batch.skipped", operation);
						consecutiveFailures = 0;
						increment("ocpx.storage.batch.success", operation);
						continue;
					}
					try {
						writer.accept(data);
						// 数据库写入全部成功后才从 List 头部确认本次原始读取数量。
						redis.opsForList().trim(queueKey, jsons.size(), -1);
						consecutiveFailures = 0;
						increment("ocpx.storage.batch.success", operation);
						if (skipped > 0) {
							otherLog.warn("{} skipped records slot:{} skipped:{} batch:{}", operation, slot, skipped,
									jsons.size());
						}
					}
					catch (Exception exception) {
						consecutiveFailures++;
						increment("ocpx.storage.batch.failure", operation);
						errorLog.error("{} database batch failed slot:{} size:{} attempt:{}/{} cause:{}", operation,
								slot, data.size(), consecutiveFailures, maxBatchRetries + 1, summarize(exception));
						if (consecutiveFailures > maxBatchRetries) {
							break;
						}
						increment("ocpx.storage.batch.retry", operation);
						backoff(consecutiveFailures);
					}
				}
				catch (RedisCommandTimeoutException exception) {
					errorLog.error("{} redis timeout slot:{} size:{} cause:{}", operation, slot, batchSize,
						summarize(exception));
					break;
				}
				catch (RuntimeException exception) {
					errorLog.error("{} batch failed slot:{} size:{} cause:{}", operation, slot, batchSize,
						summarize(exception));
					break;
				}
			}
		}
		catch (Exception exception) {
			errorLog.error("{} error slot:{} size:{} cause:{}", operation, slot, batchSize, summarize(exception));
		}
		finally {
			if (StringUtils.hasText(tempKey)) {
				// 保留旧行为：直接删除锁键，不校验当前锁值是否仍属于本实例。
				redis.delete(tempKey);
			}
		}
	}

	private void backoff(int attempt) {
		if (retryBackoffMs <= 0) {
			return;
		}
		long delay = Math.min(retryBackoffMs * (1L << Math.min(attempt - 1, 5)), 30_000L);
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private void recordDepth(String operation, long count) {
		if (meterRegistry != null) {
			meterRegistry.summary("ocpx.storage.queue.depth", "operation", operation).record(count);
		}
	}

	private void increment(String name, String operation) {
		if (meterRegistry != null) {
			meterRegistry.counter(name, "operation", operation).increment();
		}
	}

	private String summarize(Throwable exception) {
		String message = exception.getMessage();
		if (!StringUtils.hasText(message)) {
			return exception.getClass().getSimpleName();
		}
		message = message.replaceAll("[\\r\\n\\t]+", " ");
		if (message.length() > 256) {
			message = message.substring(0, 256);
		}
		return exception.getClass().getSimpleName() + ":" + message;
	}

}
