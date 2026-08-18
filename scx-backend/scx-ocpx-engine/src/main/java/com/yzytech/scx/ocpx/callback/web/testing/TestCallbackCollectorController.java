package com.yzytech.scx.ocpx.callback.web.testing;

import com.yzytech.scx.ocpx.callback.service.TestCallbackCollectorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 完整测试专用的受控媒体 Callback 接收端。 */
@RestController
@RequiredArgsConstructor
public class TestCallbackCollectorController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final TestCallbackCollectorService collectorService;

	@GetMapping("/test-callback")
	public ResponseEntity<Void> collect(@RequestParam("request_id") String requestId, @RequestParam String token,
			@RequestParam("mId") String mediaId, @RequestParam("tId") String monitorTaskId,
			@RequestParam("aId") String advertiserId, @RequestParam("lId") String promotionLinkId,
			@RequestParam("event_type") Integer eventType,
			@RequestParam(value = "currency_amount", required = false) BigDecimal currencyAmount,
			@RequestParam("delivery_signature") String deliverySignature) {
		try {
			collectorService.record(requestId, token, mediaId, monitorTaskId, advertiserId, promotionLinkId, eventType,
					currencyAmount, deliverySignature);
			return ResponseEntity.noContent().build();
		}
		catch (TestCallbackCollectorService.CallbackNotReadyException exception) {
			log.warn("Test callback rejected because the passed fact is unavailable");
			return ResponseEntity.status(409).build();
		}
		catch (IllegalArgumentException exception) {
			log.warn("Test callback rejected because its identity or route is invalid");
			return ResponseEntity.badRequest().build();
		}
		catch (RuntimeException exception) {
			log.error("Test callback collector failed without persisting request parameters", exception);
			return ResponseEntity.internalServerError().build();
		}
	}

}
