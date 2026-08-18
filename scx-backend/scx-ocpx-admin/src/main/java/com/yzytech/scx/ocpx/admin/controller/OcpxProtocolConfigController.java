package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.ocpx.admin.dto.ProtocolConfigRequests;
import com.yzytech.scx.ocpx.admin.dto.ProtocolConfigViews;
import com.yzytech.scx.ocpx.admin.service.OcpxProtocolConfigService;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/protocol-configs")
public class OcpxProtocolConfigController {

	public static final String MANAGE_PERMISSION = "ocpx:protocol-config:manage";

	private final OcpxProtocolConfigService service;

	@GetMapping
	@SaCheckPermission(MANAGE_PERMISSION)
	public Result<List<ProtocolConfigViews.Item>> list() {
		return Result.success(service.list());
	}

	@PutMapping("/{protocol}/allowed-hosts")
	@SaCheckPermission(MANAGE_PERMISSION)
	@SysLog("修改 OCPX 协议允许域名")
	public Result<ProtocolConfigViews.Item> updateAllowedHosts(@PathVariable OcpxUpstreamProtocol protocol,
			@Valid @RequestBody ProtocolConfigRequests.UpdateAllowedHosts request) {
		return Result.success(service.updateAllowedHosts(protocol, request));
	}

}
