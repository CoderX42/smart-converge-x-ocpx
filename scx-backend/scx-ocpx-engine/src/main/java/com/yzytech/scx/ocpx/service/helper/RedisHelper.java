package com.yzytech.scx.ocpx.service.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.constant.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Slf4j
@Service
public class RedisHelper {

	private final StringRedisTemplate redis;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public RedisHelper(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public double getAdvertisingRadio(String advertiserId, String channelId) {
		double radio = 1.0F;
		String value = redis.opsForValue().get(RedisConstants.ADVERTISER_RADIO + advertiserId + "_" + channelId);
		if (StringUtils.hasText(value)) {
			radio = Double.parseDouble(value);
		}
		return radio;
	}

	public int[] getAdvertisingOriginal(String advertiserId, String channelId) {
		int[] original = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		String advertiserOriginal = redis.opsForValue()
			.get(RedisConstants.ADVERTISER_ORIGINAL + advertiserId + "_" + channelId);
		if (StringUtils.hasText(advertiserOriginal)) {
			try {
				original = objectMapper.readValue(advertiserOriginal, int[].class);
			}
			catch (IOException exception) {
				exception.printStackTrace();
			}
		}
		return original;
	}

	public int[] getAdvertisingFixed(String advertiserId, String channelId) {
		int[] fixed = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		String advertiserFixed = redis.opsForValue()
			.get(RedisConstants.ADVERTISER_FIXED + advertiserId + "_" + channelId);
		if (StringUtils.hasText(advertiserFixed)) {
			try {
				fixed = objectMapper.readValue(advertiserFixed, int[].class);
			}
			catch (IOException exception) {
				exception.printStackTrace();
			}
		}
		return fixed;
	}

	public void setAdvertisingOriginal(String advertiserId, String channelId, int[] values) {
		try {
			String json = objectMapper.writeValueAsString(values);
			redis.opsForValue().set(RedisConstants.ADVERTISER_ORIGINAL + advertiserId + "_" + channelId, json);
		}
		catch (JsonProcessingException exception) {
			exception.printStackTrace();
		}
	}

	public void setAdvertisingFixed(String advertiserId, String channelId, int[] values) {
		try {
			String json = objectMapper.writeValueAsString(values);
			redis.opsForValue().set(RedisConstants.ADVERTISER_FIXED + advertiserId + "_" + channelId, json);
		}
		catch (JsonProcessingException exception) {
			exception.printStackTrace();
		}
	}

}
