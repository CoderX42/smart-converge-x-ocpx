
package com.yzytech.scx.common.redis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisLuaConfig {

	@Bean
	public DefaultRedisScript<Long> reduceStockLuaScript() {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setResultType(Long.class);
		script.setScriptText("for i = 1, #KEYS do " + " local stock = tonumber(redis.call('GET', KEYS[i]) or '0') "
				+ " local num = tonumber(ARGV[i]) " + " if stock < num then return 0 end " + "end "
				+ "for i = 1, #KEYS do " + " redis.call('DECRBY', KEYS[i], ARGV[i]) " + "end " + "return 1");
		return script;
	}

	@Bean
	public DefaultRedisScript<Long> rollbackStockLuaScript() {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setResultType(Long.class);

		// KEYS: SKU 和 SPU 的库存 key
		// ARGV: 对应的回退数量
		script.setScriptText("for i = 1, #KEYS do " + "local stock = tonumber(redis.call('GET', KEYS[i]) or '0') "
				+ "local num = tonumber(ARGV[i]) " + "redis.call('INCRBY', KEYS[i], num) " + "end " + "return 1");

		return script;
	}

}
