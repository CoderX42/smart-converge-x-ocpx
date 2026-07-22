package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.dto.DeviceIdentifier;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class DeviceIdentifierService {

	private static final Map<String, Set<String>> TYPES = Map.of("ANDROID",
			Set.of("OAID", "GAID", "ANDROID_ID", "IMEI"), "IOS", Set.of("IDFA", "IDFV", "CAID"));

	private final TrackingProperties properties;

	public ResolvedDevice resolve(DeviceIdentifier input, String clientId) {
		if (input == null)
			throw new IllegalArgumentException("device 必填");
		String platform = upper(input.getPlatform());
		String idType = upper(input.getIdType());
		if (!TYPES.containsKey(platform) || !TYPES.get(platform).contains(idType))
			throw new IllegalArgumentException("设备平台与标识类型不匹配");
		if (!"SHA256".equals(upper(input.getHashAlgorithm())))
			throw new IllegalArgumentException("设备标识仅支持 SHA256");
		String idHash = input.getIdHash() == null ? "" : input.getIdHash().trim();
		if (!idHash.matches("[0-9a-f]{64}"))
			throw new IllegalArgumentException("device.idHash 必须是 64 位小写 SHA-256 十六进制值");
		if (idHash.chars().distinct().count() == 1)
			throw new IllegalArgumentException("device.idHash 不能是全零或单一重复值");
		if ("CAID".equals(idType) && !StringUtils.hasText(input.getIdVersion()))
			throw new IllegalArgumentException("CAID 必须携带 idVersion");
		if ("IMEI".equals(idType) && !properties.isImeiEnabled())
			throw new IllegalArgumentException("IMEI 标识未获准启用");
		String idVersion = StringUtils.hasText(input.getIdVersion()) ? input.getIdVersion().trim() : "";
		String internal = derive(clientId + "|" + platform + "|" + idType + "|" + idVersion + "|" + idHash);
		return new ResolvedDevice(platform, idType, idVersion, properties.getDeviceKeyVersion(), internal);
	}

	private String derive(String input) {
		if (!StringUtils.hasText(properties.getDeviceHmacKey()))
			throw new IllegalStateException("未配置设备 HMAC 密钥");
		try {
			byte[] key = Base64.getDecoder().decode(properties.getDeviceHmacKey());
			if (key.length < 32)
				throw new IllegalStateException("设备 HMAC 密钥至少需要 256 位");
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest)
				result.append(String.format("%02x", value));
			return result.toString();
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("设备 HMAC 密钥必须使用 Base64", exception);
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("设备标识派生失败", exception);
		}
	}

	private String upper(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	public record ResolvedDevice(String platform, String idType, String idVersion, String keyVersion,
			String deviceKeyHash) {
	}

}
