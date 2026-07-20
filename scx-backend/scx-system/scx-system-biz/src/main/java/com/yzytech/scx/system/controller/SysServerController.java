

package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.system.api.vo.server.Server;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CPU相关信息
 *
 * @since 2022/5/21 15:35
 */
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/sysserver", "/system/sysserver" })
public class SysServerController {

	@GetMapping
	@SaCheckPermission("system:sysserver:get")
	public Result<Server> getServerInfo() throws Exception {
		Server server = new Server();
		server.copyTo();
		return Result.success(server);
	}

}
