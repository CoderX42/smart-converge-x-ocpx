package com.yzytech.scx.ocpx.callback.service;

import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import com.yzytech.scx.ocpx.shared.redis.RedisHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 使用单个 Redis Lua 脚本原子维护分动作的原始量和扣量放行量。 */
@Slf4j
@Service
public class DeductionDecisionService {

	private static final DefaultRedisScript<List> DECIDE_SCRIPT = new DefaultRedisScript<>("""
			local existing = redis.call('GET', KEYS[2])
			if existing then
			  local separator = string.find(existing, '|', 1, true)
			  if separator then
			    return {string.sub(existing, 1, separator - 1), string.sub(existing, separator + 1), '1', '0'}
			  end
			end
			local ratioRaw = redis.call('HGET', KEYS[1], 'ratio')
			if not ratioRaw then
			  ratioRaw = ARGV[2]
			  redis.call('HSET', KEYS[1], 'ratio', ratioRaw)
			end
			local ratio = tonumber(ratioRaw)
			local invalid = 0
			if not ratio or ratio ~= ratio or ratio < 0 or ratio > 1 then
			  ratio = 1
			  invalid = 1
			  redis.call('HSET', KEYS[1], 'ratio', '1')
			end
			local originalField = 'original:' .. ARGV[1]
			local passedField = 'passed:' .. ARGV[1]
			local original = tonumber(redis.call('HGET', KEYS[1], originalField) or ARGV[4])
			local passed = tonumber(redis.call('HGET', KEYS[1], passedField) or ARGV[5])
			if not original or original ~= original or original < 0 then original = 0 end
			if not passed or passed ~= passed or passed < 0 then passed = 0 end
			if passed > original then passed = original end
			local nextOriginal = original + 1
			local decision = 'DEDUCTED'
			if ratio >= 1 or ((passed + 1) / nextOriginal) <= ratio then
			  passed = passed + 1
			  decision = 'PASSED'
			end
			redis.call('HSET', KEYS[1], originalField, tostring(nextOriginal), passedField, tostring(passed))
			redis.call('SET', KEYS[2], decision .. '|' .. tostring(ratio))
			return {decision, tostring(ratio), '0', tostring(invalid)}
			""", List.class);

	private final StringRedisTemplate redis;

	private final RedisHelper legacyRedisHelper;

	public DeductionDecisionService(StringRedisTemplate redis, RedisHelper legacyRedisHelper) {
		this.redis = redis;
		this.legacyRedisHelper = legacyRedisHelper;
	}

	public Result decide(String factId, String promotionLinkId, String mediaId, int actionIndex) {
		if (actionIndex < 0 || actionIndex >= RedisHelper.ACTION_CAPACITY) {
			throw new IllegalArgumentException("callback action index is outside deduction capacity");
		}
		String routeTag = promotionLinkId + ":" + mediaId;
		String stateKey = RedisConstants.ROUTE_DEDUCTION_PREFIX + "{" + routeTag + "}:state";
		String decisionKey = RedisConstants.ROUTE_DEDUCTION_PREFIX + "{" + routeTag + "}:decision:" + factId;
		String originalField = "original:" + actionIndex;
		String passedField = "passed:" + actionIndex;
		HashOperations<String, Object, Object> hashes = redis.opsForHash();
		double legacyRatio = hashes.get(stateKey, RedisConstants.ROUTE_DEDUCTION_RATIO_FIELD) == null
				? legacyRedisHelper.getRouteRatio(promotionLinkId, mediaId) : 1.0d;
		long originalSeed = 0;
		long passedSeed = 0;
		if (!Boolean.TRUE.equals(hashes.hasKey(stateKey, originalField))
				|| !Boolean.TRUE.equals(hashes.hasKey(stateKey, passedField))) {
			int[] legacyOriginal = legacyRedisHelper.getRouteOriginalCounts(promotionLinkId, mediaId);
			int[] legacyPassed = legacyRedisHelper.getRouteDeliveredCounts(promotionLinkId, mediaId);
			originalSeed = Math.max(0, legacyOriginal[actionIndex]);
			passedSeed = Math.max(0, Math.min(originalSeed, legacyPassed[actionIndex]));
		}
		List<?> values = redis.execute(DECIDE_SCRIPT, List.of(stateKey, decisionKey), String.valueOf(actionIndex),
				String.valueOf(legacyRatio), String.valueOf(originalSeed), String.valueOf(passedSeed));
		if (values == null || values.size() < 4) {
			throw new IllegalStateException("media callback deduction script returned no decision");
		}
		DeductionDecision decision = DeductionDecision.valueOf(String.valueOf(values.get(0)));
		BigDecimal ratio = new BigDecimal(String.valueOf(values.get(1))).setScale(6, RoundingMode.HALF_UP);
		boolean replay = "1".equals(String.valueOf(values.get(2)));
		boolean invalidRatio = "1".equals(String.valueOf(values.get(3)));
		if (invalidRatio) {
			log.warn("invalid media callback ratio failed open linkId:{} mediaId:{}", promotionLinkId, mediaId);
		}
		return new Result(decision, ratio, replay);
	}

	/** Remove a completed decision key; pending facts retain it for exact replay. */
	public void complete(String factId, String promotionLinkId, String mediaId) {
		String routeTag = promotionLinkId + ":" + mediaId;
		String decisionKey = RedisConstants.ROUTE_DEDUCTION_PREFIX + "{" + routeTag + "}:decision:" + factId;
		try {
			redis.delete(decisionKey);
		}
		catch (RuntimeException exception) {
			log.warn("completed media deduction decision key cleanup failed factId:{}", factId, exception);
		}
	}

	public record Result(DeductionDecision decision, BigDecimal ratio, boolean replay) {
	}

}
