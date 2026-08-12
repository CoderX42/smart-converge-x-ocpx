package com.yzytech.scx.ocpx.task;

import com.yzytech.scx.ocpx.constant.RedisConstants;
import com.yzytech.scx.ocpx.constant.SystemConstants;
import com.yzytech.scx.ocpx.support.SystemMemory;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Async
@Slf4j
@Component
@ConditionalOnProperty(name = "app.jobs.reset-enabled", havingValue = "true", matchIfMissing = true)
public class ResetRedisKeyTask {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final StringRedisTemplate redis;

	private final SystemMemory systemMemory;

	public ResetRedisKeyTask(StringRedisTemplate redis, SystemMemory systemMemory) {
		this.redis = redis;
		this.systemMemory = systemMemory;
	}

	@Scheduled(cron = "30 0 0 * * ?")
	public void clearKey() {
		String lockKey = "rtb{clearKey}";
		Boolean check = redis.opsForValue().setIfAbsent(lockKey, "1", 15, TimeUnit.MINUTES);
		if (check != null && !check) {
			return;
		}
		try {
			log.info("adxclear");
			for (int index = 0; index < SystemConstants.REPEAT_REPORT_BUCKET; index++) {
				redis.unlink(RedisConstants.REPEAT_REPORT + index);
			}
		}
		catch (Exception exception) {
			errorLog.error("adxclear error:{}", exception.getMessage(), exception);
		}
		finally {
			log.info("adxclear end");
		}
	}

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

	@Scheduled(cron = "0 10 0 * * ?")
	public void initData() {
		try {
			systemMemory.init();
		}
		catch (Exception exception) {
			errorLog.error("initData error:{}", exception.getMessage(), exception);
		}
	}

	@Scheduled(cron = "0 0 0 * * ?")
	public void zeroRadio() {
		Collection<String> keys = redis.keys(RedisConstants.ADVERTISER_ORIGINAL + "*");
		redis.delete(keys);
		keys = redis.keys(RedisConstants.ADVERTISER_FIXED + "*");
		redis.delete(keys);
	}

}
