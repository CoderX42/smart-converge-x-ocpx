

package com.yzytech.scx.system.controller.app;

import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.service.ISysRegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行政区划
 *
 * @date 2025/5/3
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/region")
@Tag(description = "region", name = "行政区划")
public class AppSysRegionController {

	private final ISysRegionService sysRegionService;

	@Operation(summary = "树形结构行政区划列表")
	@GetMapping("/tree")
	public Result tree() {
		return Result.success(sysRegionService.getTree(null));
	}

}
