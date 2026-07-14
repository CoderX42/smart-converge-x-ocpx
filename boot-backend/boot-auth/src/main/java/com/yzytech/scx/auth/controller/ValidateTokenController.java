

package com.yzytech.scx.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.common.core.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检验token
 *
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/validate-token")
@Tag(description = "validate-token", name = "检验token")
public class ValidateTokenController {

	@Operation(summary = "检验是否登录")
	@GetMapping
	public Result<Boolean> validateToken() {
		return Result.success(StpUtil.isLogin());
	}

}
