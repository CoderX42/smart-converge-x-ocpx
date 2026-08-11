package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Fail-closed boundary until the DP supplier and security decision is accepted. */
@RestController
@RequestMapping("/ocpx/tools/dp")
public class OcpxDpBlockedController {

	@PostMapping("/convert")
	@ResponseStatus(HttpStatus.CONFLICT)
	@SaCheckPermission("ocpx:tool:dp:convert")
	public Result<Void> convert() {
		return Result.fail(HttpStatus.CONFLICT.value(),
				"OCPX_DP_BLOCKED_BY_DECISION: supplier protocol and security decision are not accepted");
	}

}
