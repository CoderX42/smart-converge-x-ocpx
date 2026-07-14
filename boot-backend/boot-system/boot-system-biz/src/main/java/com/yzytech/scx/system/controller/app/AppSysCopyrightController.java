

package com.yzytech.scx.system.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.entity.SysCopyright;
import com.yzytech.scx.system.api.enums.CopyrightClientTypeEnum;
import com.yzytech.scx.system.service.ISysCopyrightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 系统版权
 *
 * @date 2025/5/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/copyright")
@Tag(description = "appCopyright", name = "系统版权")
public class AppSysCopyrightController {

	private final ISysCopyrightService sysCopyrightService;

	@Operation(summary = "系统版权查询")
	@GetMapping("/{clientType}")
	public Result getById(@PathVariable String clientType) {
		if (!StringUtils.hasText(clientType)) {
			return Result.fail("客户端类型不能为空");
		}
		CopyrightClientTypeEnum copyrightClientTypeEnum = CopyrightClientTypeEnum.fromCode(clientType);
		SysCopyright sysCopyright = sysCopyrightService.getOne(Wrappers.<SysCopyright>lambdaQuery()
			.eq(SysCopyright::getClientType, copyrightClientTypeEnum.getCode()));
		if (Objects.isNull(sysCopyright)) {
			return Result.fail("版权不存在");
		}
		return Result.success(sysCopyright);
	}

}
