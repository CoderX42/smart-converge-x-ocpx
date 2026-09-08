package com.yzytech.scx.ocpx.oppo.callback;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.delivery.DownstreamDeliveryService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialResolver;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.persistence.mapper.OppoUploadActiveDataDeliverySqlMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;

/** OPPO uploadActiveData 回传；账户和链接配置复用现有 RTA/推广链接表。 */
@Service
public class OppoUploadActiveDataDeliveryService extends DownstreamDeliveryService {

	private final OppoUploadActiveDataDeliverySqlMapper sqlMapper;

	private final PartnerCredentialResolver credentialResolver;

	private final OcpxFlowLogWriter flowLogWriter;

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

	public OppoUploadActiveDataDeliveryService(OppoUploadActiveDataDeliverySqlMapper sqlMapper,
			PartnerCredentialResolver credentialResolver, OcpxFlowLogWriter flowLogWriter) {
		this.sqlMapper = sqlMapper;
		this.credentialResolver = credentialResolver;
		this.flowLogWriter = flowLogWriter;
	}

	public boolean supports(CallbackReq request) {
		try {
			return request != null && request.getFeedback() != null && !find(request.getFeedback().getLId()).isEmpty();
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	@Override
	public void deliver(CallbackReq request) {
		if (!supports(request) || request.getActionType() == null
				|| !List.of(ActionTypeEnum.ACTIVATE, ActionTypeEnum.REGISTER, ActionTypeEnum.PAYMENT)
					.contains(request.getActionType()))
			return;
		Config config = find(request.getFeedback().getLId()).get(0);
		String secret = credentialResolver.oppoSecret(config.secretRef()).orElse(null);
		if (!StringUtils.hasText(secret)) {
			flowLogWriter.downstreamDelivery(request, false, null, "OPPO_CREDENTIAL_MISSING", 0L, null, null);
			return;
		}
		long timestamp = System.currentTimeMillis();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("userIdType", 2);
		body.put("keyTrackInfo", first(request.getFeedback().getOaidMd5(), request.getFeedback().getImeiMd5()));
		body.put("requestId", request.getFeedback().getReqId());
		body.put("dataType", dataType(request.getActionType()));
		body.put("pkg", config.pkg());
		body.put("adId", config.adId());
		body.put("channel", config.channel());
		body.put("ascribeType", config.ascribeType());
		body.put("appType", config.appType());
		if (request.getActionType() == ActionTypeEnum.PAYMENT) {
			body.put("payAmount", request.getCurrencyAmount() == null ? BigDecimal.ZERO : request.getCurrencyAmount());
			body.put("payId", request.getFeedback().getClickId());
		}
		String payload = JSON.toJSONString(body);
		String endpoint = "https://api.ads.heytapmobi.com/api/uploadActiveData";
		try {
			HttpRequest outbound = HttpRequest.newBuilder(URI.create(endpoint))
				.timeout(Duration.ofSeconds(5))
				.header("Content-Type", "application/json")
				.header("signature", md5(payload + timestamp + secret))
				.header("timestamp", Long.toString(timestamp))
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
			long start = System.nanoTime();
			HttpResponse<String> response = httpClient.send(outbound, HttpResponse.BodyHandlers.ofString());
			flowLogWriter.downstreamDelivery(request, response.statusCode() >= 200 && response.statusCode() < 300,
					response.statusCode(), "OPPO_UPLOAD", Duration.ofNanos(System.nanoTime() - start).toMillis(),
					response.body(), endpoint);
		}
		catch (Exception e) {
			flowLogWriter.downstreamDelivery(request, false, null, "OPPO_REQUEST_FAILED", 0L, null, endpoint);
		}
	}

	private List<Config> find(String linkId) {
		return sqlMapper.selectFindAdvertiser(linkId).stream().map(rs -> {
			Map<String, Object> ext = JSON.parseObject(rs.linkExt());
			return new Config(String.valueOf(ext.getOrDefault("secretRef", rs.credentialConfigKey())),
					String.valueOf(ext.getOrDefault("pkg", "")), String.valueOf(ext.getOrDefault("adId", "")),
					String.valueOf(ext.getOrDefault("channel", "1")),
					Integer.parseInt(String.valueOf(ext.getOrDefault("ascribeType", "1"))),
					Integer.parseInt(String.valueOf(ext.getOrDefault("appType", "1"))));
		}).toList();
	}

	private int dataType(ActionTypeEnum action) {
		return action == ActionTypeEnum.ACTIVATE ? 1 : action == ActionTypeEnum.REGISTER ? 2 : 5;
	}

	private String first(String a, String b) {
		return StringUtils.hasText(a) ? a : b;
	}

	private String md5(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private record Config(String secretRef, String pkg, String adId, String channel, int ascribeType, int appType) {
	}

}
