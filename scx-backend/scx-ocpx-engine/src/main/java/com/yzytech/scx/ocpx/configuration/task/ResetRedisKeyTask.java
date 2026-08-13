package com.yzytech.scx.ocpx.configuration.task;

import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.shared.constant.SystemConstants;
import com.yzytech.scx.ocpx.configuration.bootstrap.SystemMemory;
import com.yzytech.scx.ocpx.report.mapper.FeedbackMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Engine 配置刷新与每日 Redis 状态维护任务。
 *
 * <p>
 * 任务负责清理 Feedback 去重桶、响应 Admin 发布的配置刷新标记、每日兜底重载内存索引、重置 Callback 扣量累计值，并每日清理
 * Feedback 日明细表。
 */
@Async
@Slf4j
@Component
@ConditionalOnProperty(name = "app.jobs.reset-enabled", havingValue = "true", matchIfMissing = true)
public class ResetRedisKeyTask {

	private static final DateTimeFormatter PURE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final StringRedisTemplate redis;

	private final SystemMemory systemMemory;

	private final FeedbackMapper feedbackMapper;

	public ResetRedisKeyTask(StringRedisTemplate redis, SystemMemory systemMemory, FeedbackMapper feedbackMapper) {
		this.redis = redis;
		this.systemMemory = systemMemory;
		this.feedbackMapper = feedbackMapper;
	}

	/** 每日零点由单个实例清理 100 个 Feedback 去重 Hash。 */
	@Scheduled(cron = "30 0 0 * * ?")
	public void clearKey() {
		// 花括号确保 Redis Cluster 中锁键具有稳定的 hash tag。
		String lockKey = "rtb{clearKey}";
		Boolean check = redis.opsForValue().setIfAbsent(lockKey, "1", 15, TimeUnit.MINUTES);
		if (check != null && !check) {
			return;
		}
		try {
			log.info("ocpxclear");
			for (int index = 0; index < SystemConstants.REPEAT_REPORT_BUCKET; index++) {
				redis.unlink(RedisConstants.REPEAT_REPORT + index);
			}
		}
		catch (Exception exception) {
			errorLog.error("ocpxclear error:{}", exception.getMessage(), exception);
		}
		finally {
			log.info("ocpxclear end");
		}
	}

	/**
	 * 每分钟检查 Admin 写入的共享刷新标记，等待写事务传播后再全量加载配置。
	 *
	 * <p>
	 * 五秒等待是兼容既有链路的固定延迟；刷新成功后才删除标记。
	 */
	@Scheduled(cron = "0 0/1 * * * ?")
	public void refreshMemoryData() {
		log.info("refreshMemoryData start");
		try {
			if (Boolean.TRUE.equals(redis.hasKey(RedisConstants.DATA_REFRESH))) {
				Thread.sleep(5000);
				systemMemory.init();
				redis.unlink(RedisConstants.DATA_REFRESH);
			}
		}
		catch (Exception exception) {
			errorLog.error("refreshMemoryData error:{}", exception.getMessage(), exception);
		}
		log.info("refreshMemoryData end");
	}

	/** 每日 00:10 兜底全量重载四实体内存索引。 */
	@Scheduled(cron = "0 10 0 * * ?")
	public void initData() {
		try {
			systemMemory.init();
		}
		catch (Exception exception) {
			errorLog.error("initData error:{}", exception.getMessage(), exception);
		}
	}

	/** 每日 00:10 由单个实例删除 Feedback 日明细表中前一日的全部小时明细。 */
	@Scheduled(cron = "0 10 0 * * ?")
	public void deleteFeedbackDay() {
		// 花括号确保 Redis Cluster 中锁键具有稳定的 hash tag。
		String lockKey = "rtb{deleteFeedbackDay}";
		Boolean check = redis.opsForValue().setIfAbsent(lockKey, "1", 15, TimeUnit.MINUTES);
		if (check != null && !check) {
			return;
		}
		try {
			log.info("deleteFeedbackDay start");
			String yesterday = PURE_DATE.format(LocalDate.now().minusDays(1));
			for (int hour = 0; hour < 24; hour++) {
				feedbackMapper.deleteFeedbackDay(Integer.valueOf(yesterday + String.format("%02d", hour)));
			}
		}
		catch (Exception exception) {
			errorLog.error("deleteFeedbackDay error:{}", exception.getMessage(), exception);
		}
		finally {
			log.info("deleteFeedbackDay end");
		}
	}

	/** 每日零点清空各推广链接与媒体组合的原始量和实际回传量计数。 */
	@Scheduled(cron = "0 0 0 * * ?")
	public void resetRouteCounters() {
		Collection<String> keys = redis.keys(RedisConstants.ROUTE_ORIGINAL_COUNT_PREFIX + "*");
		redis.delete(keys);
		keys = redis.keys(RedisConstants.ROUTE_DELIVERED_COUNT_PREFIX + "*");
		redis.delete(keys);
	}

}
