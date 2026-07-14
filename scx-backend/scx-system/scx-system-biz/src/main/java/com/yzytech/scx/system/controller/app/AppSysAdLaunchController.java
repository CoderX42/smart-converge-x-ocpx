

package com.yzytech.scx.system.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.entity.SysAdLaunch;
import com.yzytech.scx.system.service.ISysAdLaunchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 启动广告配置
 *
 * @date 2025/6/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/ad-launch")
@Tag(description = "ad-launch", name = "启动广告配置")
public class AppSysAdLaunchController {

	private final ISysAdLaunchService sysAdLaunchService;

	@Operation(summary = "启动广告配置查询")
	@GetMapping("/get")
	public Result get() {
		return Result.success(sysAdLaunchService.getOne(Wrappers.<SysAdLaunch>lambdaQuery()
			.eq(SysAdLaunch::getStatus, CommonConstants.NORMAL_STATUS)
			.last("limit 1")));
	}

}
