package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import com.yzytech.scx.ocpx.rta.contract.RtaState;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class RelayStore {

	private final StringRedisTemplate redis;

	private final DefaultRedisScript<String> state;

	private static final DefaultRedisScript<List> CLAIM = new DefaultRedisScript<>("""
			local ids=redis.call('ZRANGEBYSCORE',KEYS[1],'-inf',ARGV[1],'LIMIT',0,ARGV[2])
			for _,id in ipairs(ids) do redis.call('ZADD',KEYS[1],tonumber(ARGV[1])+5000,id) end
			return ids
			""", List.class);

	public RelayStore(StringRedisTemplate redis) throws java.io.IOException {
		this.redis = redis;
		state = new DefaultRedisScript<>(
				new ClassPathResource("rta/state.lua").getContentAsString(StandardCharsets.UTF_8), String.class);
	}

	public RtaState init(RtaState value) {
		return mutate("INIT", value.getReceivedAt(), value.getRelayRequestId(), RelayJson.write(value));
	}

	public RtaState get(String id) {
		String value = redis.opsForValue().get(RtaContract.PREFIX + "state:" + id);
		if (value == null)
			throw new RelayException(404, "RTA_REQUEST_NOT_FOUND");
		return RelayJson.read(value, RtaState.class);
	}

	public RtaState find(String mediaId, String requestId) {
		String id = redis.opsForValue().get(RtaContract.PREFIX + "idem:" + mediaId + ":" + requestId);
		if (id == null)
			throw new RelayException(403, "RTA_ADMISSION_MISSING");
		return get(id);
	}

	public RtaState finish(String id, String status, String reason, List<String> matched, String hash, long now) {
		return mutate("FINISH", now, id, status, reason, RelayJson.write(matched), hash);
	}

	public RtaState mutate(String op, long now, String id, String... arguments) {
		java.util.ArrayList<String> args = new java.util.ArrayList<>(List.of(op, Long.toString(now), id));
		args.addAll(List.of(arguments));
		String result = redis.execute(state, List.of(RtaContract.PREFIX), args.toArray());
		if (result == null)
			throw new RelayException(503, "RTA_STORAGE_UNAVAILABLE");
		if (result.equals("DISPATCHED"))
			throw new RelayException(409, "RTA_DISPATCH_ALREADY_STARTED");
		if (result.equals("CONFLICT"))
			throw new RelayException(409, "RTA_REQUEST_CONFLICT");
		if (result.equals("EXPIRED"))
			throw new RelayException(410, "RTA_REQUEST_EXPIRED");
		if (result.equals("MISSING"))
			throw new RelayException(404, "RTA_REQUEST_NOT_FOUND");
		return RelayJson.read(result, RtaState.class);
	}

	@SuppressWarnings("unchecked")
	public List<String> claim(String queue, long now, int count) {
		List<String> values = redis.execute(CLAIM, List.of(RtaContract.PREFIX + queue), Long.toString(now),
				Integer.toString(count));
		return values == null ? List.of() : values;
	}

}
