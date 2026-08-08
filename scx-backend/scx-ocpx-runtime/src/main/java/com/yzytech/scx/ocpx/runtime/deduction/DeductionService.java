package com.yzytech.scx.ocpx.runtime.deduction;

import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class DeductionService {

	private static final DefaultRedisScript<String> DECISION_SCRIPT = new DefaultRedisScript<>("""
			local existing = redis.call('GET', KEYS[1])
			if existing then return existing end
			local original = redis.call('HINCRBY', KEYS[2], 'original', 1)
			local forwarded = tonumber(redis.call('HGET', KEYS[2], 'forwarded') or '0')
			local shouldForward = '0'
			if ((forwarded + 1) * 10000) <= (original * tonumber(ARGV[1])) then
			  redis.call('HINCRBY', KEYS[2], 'forwarded', 1)
			  forwarded = forwarded + 1
			  shouldForward = '1'
			end
			local result = shouldForward .. '|' .. original .. '|' .. forwarded
			redis.call('SET', KEYS[1], result, 'EX', ARGV[2])
			redis.call('EXPIRE', KEYS[2], ARGV[2])
			return result
			""", String.class);

	private final StringRedisTemplate redisTemplate;

	private final OcpxPersistenceRepository repository;

	private final ReactiveBlockingExecutor blockingExecutor;

	public DeductionService(StringRedisTemplate redisTemplate, OcpxPersistenceRepository repository,
			ReactiveBlockingExecutor blockingExecutor) {
		this.redisTemplate = redisTemplate;
		this.repository = repository;
		this.blockingExecutor = blockingExecutor;
	}

	/**
	 * 读取既有决策或通过 Redis 原子脚本生成扣量决策。
	 *
	 * <p>
	 * JDBC 与 Redis 都是同步客户端，因此整个临界段由公共执行器隔离，调用方不需要关心线程切换。
	 * </p>
	 */
	public Mono<Decision> decide(String callbackEventId, RouteSnapshot route, OcpxEventType eventType) {
		return blockingExecutor.call(() -> decideBlocking(callbackEventId, route, eventType));
	}

	private Decision decideBlocking(String callbackEventId, RouteSnapshot route, OcpxEventType eventType) {
		var existing = repository.findDeductionDecision(callbackEventId);
		if (existing.isPresent()) {
			return new Decision(existing.get(), "EXISTING_DECISION");
		}
		BigDecimal ratio = route.deductionRatio() == null ? BigDecimal.ONE : route.deductionRatio();
		if (ratio.compareTo(BigDecimal.ONE) >= 0) {
			return new Decision(true, "RATIO_FULL");
		}
		if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
			return new Decision(false, "RATIO_ZERO");
		}
		String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
		String prefix = "scx:ocpx:deduction:" + route.routeId() + ":" + eventType.name() + ":" + route.routeVersion()
				+ ":" + date;
		try {
			int basisPoints = ratio.movePointRight(4).intValue();
			String result = redisTemplate.execute(DECISION_SCRIPT,
					List.of(prefix + ":decision:" + callbackEventId, prefix + ":counter"),
					Integer.toString(basisPoints), Long.toString(172800));
			if (result == null) {
				return new Decision(true, "REDIS_UNAVAILABLE_FAIL_OPEN");
			}
			return new Decision(result.startsWith("1|"), "RATIO_COUNTER");
		}
		catch (Exception e) {
			return new Decision(true, "REDIS_UNAVAILABLE_FAIL_OPEN");
		}
	}

	public record Decision(boolean forwarded, String reason) {
	}

}
