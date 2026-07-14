

package com.yzytech.scx.system.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.entity.SysAgreement;
import com.yzytech.scx.system.api.enums.AgreementTypeEnum;
import com.yzytech.scx.system.service.ISysAgreementService;
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
 * 系统协议
 *
 * @date 2025/5/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/agreement")
@Tag(description = "appAgreement", name = "系统协议-API")
public class AppSysAgreementController {

	private final ISysAgreementService sysAgreementService;

	@Operation(summary = "系统协议查询")
	@GetMapping("/{type}")
	public Result getAgreement(@PathVariable String type) {
		if (!StringUtils.hasText(type)) {
			return Result.fail("类型不能为空");
		}
		AgreementTypeEnum agreementTypeEnum = AgreementTypeEnum.fromCode(type);
		SysAgreement sysAgreement = sysAgreementService
			.getOne(Wrappers.<SysAgreement>lambdaQuery().eq(SysAgreement::getType, agreementTypeEnum.getCode()));
		if (Objects.isNull(sysAgreement)) {
			return Result.fail("协议不存在");
		}
		return Result.success(sysAgreement);
	}

}
