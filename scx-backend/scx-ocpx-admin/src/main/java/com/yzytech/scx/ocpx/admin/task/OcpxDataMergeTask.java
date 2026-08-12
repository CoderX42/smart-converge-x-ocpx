package com.yzytech.scx.ocpx.admin.task;

import com.yzytech.scx.ocpx.admin.service.OcpxReportMergeService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Async
@Slf4j
@Component
public class OcpxDataMergeTask {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final StringRedisTemplate redis;

	private final OcpxReportMergeService reportService;

	private final long reportDelayMinutes;

	public OcpxDataMergeTask(StringRedisTemplate redis, OcpxReportMergeService reportService,
			@Value("${app.jobs.report-delay-minutes:1}") long reportDelayMinutes) {
		this.redis = redis;
		this.reportService = reportService;
		this.reportDelayMinutes = reportDelayMinutes;
	}

	@Scheduled(cron = "${app.jobs.report-minute-cron:0 0/1 * * * ?}")
	public void groupReportMinute() {
		String lockKey = "admin{groupReportMinute}";
		Boolean check = redis.opsForValue().setIfAbsent(lockKey, "1", 50, TimeUnit.SECONDS);
		if (check != null && !check) {
			return;
		}
		try {
			log.info("groupReportMinute start");
			String groupTime = LocalDateTime.now()
				.minus(reportDelayMinutes, ChronoUnit.MINUTES)
				.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			reportService.groupReportMinute(groupTime, 0);
			log.info("groupReportMinute end");
		}
		catch (Exception exception) {
			errorLog.error("groupReportMinute error:{}", exception.getMessage(), exception);
		}
		finally {
			redis.unlink(lockKey);
		}
	}

	@Scheduled(cron = "0 10 * * * ?")
	public void groupReport() {
		String lockKey = "admin{groupReport}";
		Boolean check = redis.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.MINUTES);
		if (check != null && !check) {
			return;
		}
		try {
			log.info("groupReport start");
			String groupTime = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
			reportService.groupReport(groupTime, 0);
			log.info("groupReport end");
		}
		catch (Exception exception) {
			errorLog.error("groupReport error:{}", exception.getMessage(), exception);
		}
		finally {
			redis.unlink(lockKey);
		}
	}

}
