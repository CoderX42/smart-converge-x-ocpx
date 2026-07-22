package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.dto.DeviceIdentifier;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceIdentifierServiceTest {

	private final TrackingProperties properties = new TrackingProperties();

	private DeviceIdentifierService service;

	@BeforeEach
	void setUp() {
		properties.setDeviceHmacKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
		service = new DeviceIdentifierService(properties);
	}

	@Test
	void derivesStableInternalKeyWithoutReturningExternalHash() {
		DeviceIdentifier input = device("ANDROID", "OAID", "ab".repeat(32), null);

		DeviceIdentifierService.ResolvedDevice first = service.resolve(input, "client-1");
		DeviceIdentifierService.ResolvedDevice second = service.resolve(input, "client-1");

		assertThat(first.deviceKeyHash()).hasSize(64).isEqualTo(second.deviceKeyHash()).isNotEqualTo(input.getIdHash());
	}

	@Test
	void rejectsPlatformMismatchRawLikeValuesAndCaidWithoutVersion() {
		assertThatThrownBy(() -> service.resolve(device("IOS", "OAID", "a".repeat(64), null), "client-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("设备平台与标识类型不匹配");
		assertThatThrownBy(() -> service.resolve(device("ANDROID", "OAID", "raw-oaid", null), "client-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("64 位小写");
		assertThatThrownBy(() -> service.resolve(device("IOS", "CAID", "ab".repeat(32), null), "client-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("CAID 必须携带 idVersion");
	}

	@Test
	void imeiIsDisabledByDefault() {
		assertThatThrownBy(() -> service.resolve(device("ANDROID", "IMEI", "ab".repeat(32), null), "client-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("IMEI 标识未获准启用");
	}

	private DeviceIdentifier device(String platform, String type, String hash, String version) {
		DeviceIdentifier input = new DeviceIdentifier();
		input.setPlatform(platform);
		input.setIdType(type);
		input.setHashAlgorithm("SHA256");
		input.setIdHash(hash);
		input.setIdVersion(version);
		return input;
	}

}
