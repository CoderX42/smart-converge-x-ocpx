package com.yzytech.scx.ocpx.runtime.ingress;

import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.model.DeviceIdentity;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;

import java.util.Locale;

public class DeviceIdentityResolver {

	private final OcpxRuntimeProperties properties;

	public DeviceIdentityResolver(OcpxRuntimeProperties properties) {
		this.properties = properties;
	}

	public DeviceIdentity resolve(FeedbackReqV1 feedback) {
		String platform = platform(feedback);
		if (platform == null) {
			return null;
		}
		Candidate candidate = switch (platform) {
			case "ANDROID" -> android(feedback);
			case "IOS" -> ios(feedback);
			default -> null;
		};
		if (candidate == null) {
			return null;
		}
		int version = properties.getDeviceHmacKeyVersion();
		String canonical = "v" + version + "|" + platform + "|" + candidate.type() + "|"
				+ normalize(candidate.value());
		return new DeviceIdentity(platform, candidate.type(), version,
				OcpxHashing.hmacSha256(properties.getDeviceHmacKey(), canonical));
	}

	private String platform(FeedbackReqV1 feedback) {
		String os = trim(feedback.getOs());
		if (os != null) {
			String normalized = os.toLowerCase(Locale.ROOT);
			if (normalized.contains("android")) {
				return "ANDROID";
			}
			if (normalized.contains("ios") || normalized.contains("iphone") || normalized.contains("ipad")) {
				return "IOS";
			}
		}
		boolean android = android(feedback) != null;
		boolean ios = ios(feedback) != null;
		if (android == ios) {
			return null;
		}
		return android ? "ANDROID" : "IOS";
	}

	private Candidate android(FeedbackReqV1 feedback) {
		return first(candidate("OAID", feedback.getOaid()), candidate("OAID_MD5", feedback.getOaidMd5()),
				candidate("GAID", feedback.getGaid()), candidate("AAID", feedback.getAaid()),
				candidate("ANDROID_ID", feedback.getAndroidId()),
				candidate("ANDROID_ID_MD5", feedback.getAndroidIdMd5()), candidate("IMEI", feedback.getImei()),
				candidate("IMEI_MD5", feedback.getImeiMd5()));
	}

	private Candidate ios(FeedbackReqV1 feedback) {
		return first(candidate("IDFA", feedback.getIdfa()), candidate("IDFA_MD5", feedback.getIdfaMd5()),
				candidate("CAID", feedback.getCaid()), candidate("IDFV", feedback.getIdfv()));
	}

	private Candidate candidate(String type, String value) {
		String trimmed = trim(value);
		return trimmed == null ? null : new Candidate(type, trimmed);
	}

	private Candidate first(Candidate... candidates) {
		for (Candidate candidate : candidates) {
			if (candidate != null) {
				return candidate;
			}
		}
		return null;
	}

	private String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private String trim(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private record Candidate(String type, String value) {
	}
}
