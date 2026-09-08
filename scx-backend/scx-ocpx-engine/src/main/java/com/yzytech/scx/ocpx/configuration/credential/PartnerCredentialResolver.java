package com.yzytech.scx.ocpx.configuration.credential;

import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/** 只在 Engine 进程内解析凭据引用，调用方不得记录返回值。 */
@Component
public class PartnerCredentialResolver {

	private final PartnerCredentialProperties properties;

	public PartnerCredentialResolver(PartnerCredentialProperties properties) {
		this.properties = properties;
	}

	public Optional<String> advertiserSecret(OcpxAdvertiser advertiser) {
		return resolve(advertiser == null ? null : advertiser.getCredentialConfigKey(), properties.getAdvertisers());
	}

	public Optional<String> oppoSecret(String reference) {
		return resolve(reference, properties.getOppo());
	}

	private Optional<String> resolve(String reference, java.util.Map<String, String> credentials) {
		if (!StringUtils.hasText(reference)) {
			return Optional.empty();
		}
		String value = credentials.get(reference);
		return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
	}

}
