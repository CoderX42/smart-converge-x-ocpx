package com.yzytech.scx.ocpx.rta.stream;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.rta.config.RtaProperties;
import com.yzytech.scx.ocpx.rta.exception.RtaInfrastructureException;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;



/** 将脱敏 RTA 决定事件写入 Redis Stream。 */
@Component
@RequiredArgsConstructor
public class RtaStreamPublisher {

	private final StringRedisTemplate redis;

	private final RtaProperties properties;

	public void publish(RtaDecisionEvent event) {
		try {
			MapRecord<String, String, String> record = StreamRecords.newRecord().in(properties.getStreamKey())
				.ofMap(Map.of("payload", JSON.toJSONString(event)));
			RecordId recordId = redis.opsForStream().add(record);
			if (recordId == null) {
				throw new IllegalStateException("RTA decision stream returned no record id");
			}
		}
		catch (RuntimeException exception) {
			throw new RtaInfrastructureException("RTA decision stream unavailable", exception);
		}
	}

}
