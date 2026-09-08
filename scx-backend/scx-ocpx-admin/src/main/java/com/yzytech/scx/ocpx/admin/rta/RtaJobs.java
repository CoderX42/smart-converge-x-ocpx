package com.yzytech.scx.ocpx.admin.rta;

import com.alibaba.fastjson2.JSON;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.yzytech.scx.ocpx.admin.mapper.RtaJobsSqlMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

/** These handlers are exclusively triggered by XXL-JOB; never by @Scheduled. */
@Component
public class RtaJobs {

	private static final String STREAM = RtaContract.PREFIX + "logs", GROUP = "sql-writers";

	private final String consumer = UUID.randomUUID().toString();

	private final StringRedisTemplate redis;

	private final RtaJobsSqlMapper sqlMapper;

	private final TransactionTemplate transaction;

	public RtaJobs(StringRedisTemplate redis, RtaJobsSqlMapper sqlMapper,
			org.springframework.transaction.PlatformTransactionManager tx) {
		this.sqlMapper = sqlMapper;
		this.redis = redis;
		transaction = new TransactionTemplate(tx);
	}

	@XxlJob("ocpxRtaLogFlushJob")
	public synchronized void flushLogs() {
		if (!Boolean.TRUE.equals(redis.hasKey(STREAM)))
			return;
		try {
			redis.opsForStream().createGroup(STREAM, ReadOffset.from("0-0"), GROUP);
		}
		catch (RuntimeException e) {
			if (!Objects.toString(e.getMessage(), "").contains("BUSYGROUP") && !causeContains(e, "BUSYGROUP"))
				throw e;
		}
		for (int batch = 0; batch < 4; batch++) {
			List<MapRecord<String, String, String>> records = readLogs();
			if (records == null || records.isEmpty())
				break;
			List<RtaJobsSqlMapper.LogRow> values = new ArrayList<>();
			List<MapRecord<String, String, String>> valid = new ArrayList<>();
			for (var record : records) {
				RtaContract.Log event;
				try {
					event = JSON.parseObject(record.getValue().get("payload"), RtaContract.Log.class);
					if (event == null || !validText(event.id(), 32) || !event.id().matches("[a-f0-9]{32}")
							|| !validText(event.relayRequestId(), 32) || !validText(event.requestId(), 128)
							|| !validText(event.mediaId(), 32) || !validText(event.advertiserId(), 32)
							|| !validText(event.upstreamId(), 32) || !validText(event.taskId(), 32)
							|| !validText(event.stage(), 32) || !validText(event.status(), 32)
							|| !validText(event.reason(), 64) || event.durationMs() < 0 || event.eventTime() <= 0
							|| event.eventTime() > System.currentTimeMillis() + 300000)
						throw new IllegalArgumentException();
				}
				catch (RuntimeException e) {
					redis.opsForStream()
						.add(STREAM + ":dlq", Map.of("sourceId", record.getId().getValue(), "reason", "INVALID_LOG"));
					ack(record);
					continue;
				}
				valid.add(record);
				if (event.eventTime() >= System.currentTimeMillis() - RtaContract.RETENTION_MS)
					values.add(new RtaJobsSqlMapper.LogRow(event.id(), event.relayRequestId(), event.requestId(),
							event.mediaId(), event.advertiserId(), event.upstreamId(), event.taskId(), event.stage(),
							event.status(), event.reason(), event.durationMs(), new Timestamp(event.eventTime())));
			}
			if (!values.isEmpty())
				transaction.executeWithoutResult(status -> sqlMapper.insertLogs(values));
			for (var record : valid)
				ack(record);

		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private List<MapRecord<String, String, String>> readLogs() {
		var pending = redis.opsForStream().pending(STREAM, GROUP, Range.unbounded(), 500);
		List<RecordId> ids = new ArrayList<>();
		for (var p : pending)
			if (p.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofSeconds(30)) >= 0)
				ids.add(p.getId());
		if (!ids.isEmpty())
			return (List) redis.opsForStream()
				.claim(STREAM, GROUP, consumer, Duration.ofSeconds(30), ids.toArray(RecordId[]::new));
		return (List) redis.opsForStream()
			.read(Consumer.from(GROUP, consumer), StreamReadOptions.empty().count(500),
					StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
	}

	private void ack(MapRecord<String, String, String> record) {
		redis.opsForStream().acknowledge(STREAM, GROUP, record.getId());
		redis.opsForStream().delete(STREAM, record.getId());
	}

	@XxlJob("ocpxRtaMetricFlushJob")
	public synchronized void flushMetrics() {
		try (Cursor<String> keys = redis.opsForSet()
			.scan(RtaContract.PREFIX + "metric-dirty", ScanOptions.scanOptions().count(500).build())) {
			int count = 0;
			while (keys.hasNext() && count++ < 2000) {
				String key = keys.next();
				Map<Object, Object> m = redis.opsForHash().entries(key);
				if (m.isEmpty())
					continue;
				long version = Long.parseLong(m.get("version").toString());
				sqlMapper.upsertMetrics(
						new RtaJobsSqlMapper.MetricSnapshot(new Timestamp(Long.parseLong(m.get("timeHour").toString())),
								Objects.toString(m.get("mediaId"), null), Objects.toString(m.get("advertiserId"), null),
								Objects.toString(m.get("taskId"), null), Objects.toString(m.get("upstreamId"), null),
								Long.parseLong(m.getOrDefault("requestCount", "0").toString()),
								Long.parseLong(m.getOrDefault("allowCount", "0").toString()),
								Long.parseLong(m.getOrDefault("denyCount", "0").toString()),
								Long.parseLong(m.getOrDefault("errorCount", "0").toString()),
								Long.parseLong(m.getOrDefault("timeoutCount", "0").toString()),
								Long.parseLong(m.getOrDefault("deliverySuccessCount", "0").toString()),
								Long.parseLong(m.getOrDefault("deliveryFailedCount", "0").toString()), version));
				redis.execute(new DefaultRedisScript<>(
						"if redis.call('HGET',KEYS[1],'version')==ARGV[1] then redis.call('SREM',KEYS[2],KEYS[1]); redis.call('EXPIRE',KEYS[1],172800); return 1 end return 0",
						Long.class), List.of(key, RtaContract.PREFIX + "metric-dirty"), Long.toString(version));
			}
		}
	}

	@XxlJob("ocpxRtaLogCleanupJob")
	public void cleanupLogs() {
		for (int i = 0; i < 10; i++)
			if (sqlMapper.deleteCleanupLogsRtaLog() < 1000)
				break;
		redis.opsForStream().trim(STREAM + ":dlq", 10000);
	}

	private boolean validText(String value, int max) {
		return value != null && !value.isBlank() && value.length() <= max
				&& !value.chars().anyMatch(Character::isISOControl);
	}

	private boolean causeContains(Throwable e, String text) {
		for (Throwable p = e; p != null; p = p.getCause())
			if (Objects.toString(p.getMessage(), "").contains(text))
				return true;
		return false;
	}

}
