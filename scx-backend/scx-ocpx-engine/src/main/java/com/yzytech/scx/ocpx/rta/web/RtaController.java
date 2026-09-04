package com.yzytech.scx.ocpx.rta.web;

import com.yzytech.scx.ocpx.rta.application.RtaDecisionService;
import com.yzytech.scx.ocpx.rta.exception.RtaConflictException;
import com.yzytech.scx.ocpx.rta.exception.RtaInfrastructureException;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;



/** 内网/网关保护的 RTA 决策入口。 */
@RestController
public class RtaController {

	private final RtaDecisionService service;

	public RtaController(RtaDecisionService service) {
		this.service = service;
	}

	@PostMapping("/rta")
	public ResponseEntity<?> decide(@RequestBody RtaDecisionRequest request, HttpServletRequest servletRequest) {
		try {
			if (servletRequest.getContentLengthLong() > 64 * 1024L) {
				return ResponseEntity.badRequest()
					.body(new RtaErrorResponse("RTA_REQUEST_INVALID", "RTA request exceeds 64 KB"));
			}
			return ResponseEntity.ok(service.decide(request));
		}
		catch (RtaConflictException exception) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new RtaErrorResponse("RTA_REQUEST_CONFLICT", exception.getMessage()));
		}
		catch (RtaInfrastructureException exception) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new RtaErrorResponse("RTA_INFRA_UNAVAILABLE", "RTA dependency unavailable"));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest()
				.body(new RtaErrorResponse("RTA_REQUEST_INVALID", exception.getMessage()));
		}
	}

	/** Jackson 反序列化失败发生在进入方法前，也必须保持 RTA 的固定 400 契约。 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<RtaErrorResponse> unreadable(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest()
			.body(new RtaErrorResponse("RTA_REQUEST_INVALID", "RTA request body is invalid"));
	}

	private record RtaErrorResponse(String code, String message) {
	}

}
