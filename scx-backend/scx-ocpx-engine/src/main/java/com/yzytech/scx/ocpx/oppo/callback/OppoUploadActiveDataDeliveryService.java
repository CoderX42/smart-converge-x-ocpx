package com.yzytech.scx.ocpx.oppo.callback;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.delivery.DownstreamDeliveryService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialProperties;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialProperties.OppoUpload;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.rta.relay.RelayHttpTransport;
import com.yzytech.scx.ocpx.oppo.OppoRtaService;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** OPPO v3.23 media upload. No advertiser credential or link-level downstream routing. */
@Service
public class OppoUploadActiveDataDeliveryService extends DownstreamDeliveryService {

	private final PartnerCredentialProperties credentials;

	private final RelayHttpTransport transport;

	private final OcpxFlowLogWriter logs;

	public OppoUploadActiveDataDeliveryService(PartnerCredentialProperties credentials, RelayHttpTransport transport,
			OcpxFlowLogWriter logs) {
		this.credentials = credentials;
		this.transport = transport;
		this.logs = logs;
	}

	public boolean supports(CallbackReq request) {
		return request != null && request.getFeedback() != null
				&& "OPPO".equals(request.getFeedback().getDownstreamProtocol());
	}

	public record Upload(String body, Map<String, String> headers) {
	}

	@Override
	public void deliver(CallbackReq request) {
		long start = System.nanoTime();
		try {
			if (!supports(request))
				return;
			OppoUpload config = credentials.getOppoUpload().get(request.getFeedback().getDownstreamCredentialRef());
			Upload upload = build(request, config, System.currentTimeMillis());
			var response = transport.uploadOppo(upload.body(), upload.headers());
			var body = JSON.parseObject(response.body());
			Object ret = body == null ? null : body.get("ret");
			boolean success = response.status() >= 200 && response.status() < 300 && ret instanceof Integer
					&& ((Integer) ret) == 0;
			// Never log the returned message/body: it may echo encrypted device
			// identifiers or credentials.
			logs.downstreamDelivery(request, success, response.status(), success ? "OPPO_ACCEPTED" : "OPPO_REJECTED",
					(System.nanoTime() - start) / 1000000, null, null);
		}
		catch (Exception e) {
			logs.downstreamDelivery(request, false, null, "OPPO_UPLOAD_FAILED", (System.nanoTime() - start) / 1000000,
					null, null);
		}
	}

	public static Upload build(CallbackReq request, OppoUpload config, long now) throws Exception {
		if (config == null || blank(config.getSalt()) || blank(config.getAesKey()))
			throw new IllegalArgumentException("OPPO_CREDENTIAL_MISSING");
		var f = request.getFeedback();
		if (f == null || blank(f.getOppoPkg())
				|| !f.getOppoPkg().matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || blank(f.getOaidMd5())
				|| !f.getOaidMd5().matches("[a-fA-F0-9]{32}") || blank(f.getReqId()) || request.getActionTime() == null
				|| request.getActionTime() <= 0)
			throw new IllegalArgumentException("OPPO_CONTEXT_INVALID");
		long adId = Long.parseLong(f.getCreativeId());
		if (adId <= 0)
			throw new IllegalArgumentException("OPPO_AD_INVALID");
		int type = request.getActionType() == ActionTypeEnum.ACTIVATE ? 1
				: request.getActionType() == ActionTypeEnum.REGISTER ? 2
						: request.getActionType() == ActionTypeEnum.PAYMENT ? 7 : 0;
		if (type == 0)
			throw new IllegalArgumentException("OPPO_EVENT_UNSUPPORTED");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("userIdType", 1);
		body.put("ouId", encrypt(f.getOaidMd5().toLowerCase(Locale.ROOT), config.getAesKey()));
		body.put("type", 2);
		body.put("requestId", f.getReqId());
		body.put("timestamp", request.getActionTime());
		body.put("pkg", f.getOppoPkg());
		body.put("dataType", type);
		body.put("adId", adId);
		body.put("channel", 1);
		body.put("ascribeType", 1);
		body.put("appType", 1);
		body.put("dataCategory", 0);
		if (!blank(f.getIp()))
			body.put("clientIp", f.getIp());
		String payload = JSON.toJSONString(body);
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("timestamp", Long.toString(now));
		headers.put("signature", OppoRtaService.md5(payload + now + config.getSalt()));
		headers.put("Content-Type", "application/json");
		if (!blank(config.getOwnerId()) || !blank(config.getApiId()) || !blank(config.getApiKey())) {
			if (blank(config.getOwnerId()) || blank(config.getApiId()) || blank(config.getApiKey()))
				throw new IllegalArgumentException("OPPO_TOKEN_CONFIG_INCOMPLETE");
			String seconds = Long.toString(now / 1000);
			String sign = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-1")
					.digest((config.getApiId() + config.getApiKey() + seconds).getBytes(StandardCharsets.UTF_8)));
			headers.put("ownerToken",
					Base64.getEncoder()
						.encodeToString((config.getOwnerId() + "," + config.getApiId() + "," + seconds + "," + sign)
							.getBytes(StandardCharsets.UTF_8)));
		}
		return new Upload(payload, Map.copyOf(headers));
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	private static String encrypt(String value, String key) throws Exception {
		byte[] bytes = Base64.getDecoder().decode(key);
		if (bytes.length != 16)
			throw new IllegalArgumentException("OPPO_AES_KEY_INVALID");
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(bytes, "AES"));
		return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
	}

}
