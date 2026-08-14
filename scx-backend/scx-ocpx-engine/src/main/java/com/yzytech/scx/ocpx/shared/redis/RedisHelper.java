package com.yzytech.scx.ocpx.shared.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;

/**
 * Callback 扣量状态的 Redis 访问封装。
 *
 * <p>
 * 状态以“推广链接 ID + 媒体 ID”为维度保存：目标比例是标量，原始量和实际回传量是固定 19 位数组。历史 11 位数组读取时补零；数组的读取、修改和写回不是原子操作。
 */
@Slf4j
@Service
public class RedisHelper {

	public static final int ACTION_CAPACITY = 19;

	private final StringRedisTemplate redis;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public RedisHelper(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * 读取目标回传比例。
	 * @return 未配置时返回 {@code 1.0}，表示全部回传
	 */
	public double getRouteRatio(String lId, String mId) {
		double ratio = 1.0F;
		String value = redis.opsForValue().get(routeKey(RedisConstants.ROUTE_RATIO_PREFIX, lId, mId));
		if (StringUtils.hasText(value)) {
			ratio = Double.parseDouble(value);
		}
		return ratio;
	}

	/**
	 * 读取各标准动作的上游原始转化计数。
	 * @return 未配置或无法解析时返回 19 位零数组；历史 11 位数组自动补零
	 */
	public int[] getRouteOriginalCounts(String lId, String mId) {
		int[] original = new int[ACTION_CAPACITY];
		String routeOriginalCounts = redis.opsForValue()
			.get(routeKey(RedisConstants.ROUTE_ORIGINAL_COUNT_PREFIX, lId, mId));
		if (StringUtils.hasText(routeOriginalCounts)) {
			try {
				original = normalize(objectMapper.readValue(routeOriginalCounts, int[].class));
			}
			catch (IOException exception) {
				exception.printStackTrace();
			}
		}
		return original;
	}

	/**
	 * 读取各标准动作的实际媒体回传计数。
	 * @return 未配置或无法解析时返回 19 位零数组；历史 11 位数组自动补零
	 */
	public int[] getRouteDeliveredCounts(String lId, String mId) {
		int[] delivered = new int[ACTION_CAPACITY];
		String routeDeliveredCounts = redis.opsForValue()
			.get(routeKey(RedisConstants.ROUTE_DELIVERED_COUNT_PREFIX, lId, mId));
		if (StringUtils.hasText(routeDeliveredCounts)) {
			try {
				delivered = normalize(objectMapper.readValue(routeDeliveredCounts, int[].class));
			}
			catch (IOException exception) {
				exception.printStackTrace();
			}
		}
		return delivered;
	}

	/** 将各标准动作的原始转化计数数组整体写回 Redis。 */
	public void setRouteOriginalCounts(String lId, String mId, int[] values) {
		try {
			String json = objectMapper.writeValueAsString(normalize(values));
			redis.opsForValue().set(routeKey(RedisConstants.ROUTE_ORIGINAL_COUNT_PREFIX, lId, mId), json);
		}
		catch (JsonProcessingException exception) {
			exception.printStackTrace();
		}
	}

	/** 将各标准动作的实际回传计数数组整体写回 Redis。 */
	public void setRouteDeliveredCounts(String lId, String mId, int[] values) {
		try {
			String json = objectMapper.writeValueAsString(normalize(values));
			redis.opsForValue().set(routeKey(RedisConstants.ROUTE_DELIVERED_COUNT_PREFIX, lId, mId), json);
		}
		catch (JsonProcessingException exception) {
			exception.printStackTrace();
		}
	}

	private static String routeKey(String prefix, String lId, String mId) {
		return prefix + lId + ":" + mId;
	}

	private int[] normalize(int[] values) {
		return values == null ? new int[ACTION_CAPACITY] : Arrays.copyOf(values, ACTION_CAPACITY);
	}

}
