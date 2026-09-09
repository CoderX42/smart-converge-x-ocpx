package com.yzytech.scx.ocpx.rta.contract;

import java.util.*;

/** Non-secret media settings. Credentials remain in server profiles. */
public final class OppoMediaConfig {

	private OppoMediaConfig() {
	}

	public static Map<String, String> validate(Map<String, String> input) {
		if (!Set.of("ownerMode", "ownerId", "timeoutMs", "uploadCredentialRef").containsAll(input.keySet()))
			throw new IllegalArgumentException("未知 OPPO 配置字段");
		Map<String, String> c = new LinkedHashMap<>(input);
		c.putIfAbsent("ownerMode", "EMPTY");
		c.putIfAbsent("timeoutMs", "1000");
		if (!Set.of("EMPTY", "VALUE").contains(c.get("ownerMode")))
			throw new IllegalArgumentException("ownerMode 无效");
		if (c.get("ownerMode").equals("VALUE") && !c.getOrDefault("ownerId", "").matches("[0-9]{1,18}"))
			throw new IllegalArgumentException("请填写 OPPO owner_id");
		int timeout = Integer.parseInt(c.get("timeoutMs"));
		if (timeout < 100 || timeout > 5000)
			throw new IllegalArgumentException("OPPO 等待时限须为 100–5000 毫秒");
		if (!c.getOrDefault("uploadCredentialRef", "").matches("[A-Za-z0-9_-]{0,64}"))
			throw new IllegalArgumentException("回传凭据引用无效");
		return Map.copyOf(c);
	}

}
