package com.yzytech.scx.tracking.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.mapper.RtaEndpointMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import com.yzytech.scx.tracking.mapper.RtaDecisionLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/rta-endpoints")
public class RtaDecisionLogController {

	private final RtaEndpointMapper endpointMapper;

	private final RtaDecisionLogMapper logMapper;

	private final MediaScopeService scopeService;

	@GetMapping("/{id}/decision-logs")
	@SaCheckPermission("ad:rta:read")
	public Result<Page<RtaDecisionLog>> page(@PathVariable String id, @RequestParam String mediaId,
			Page<RtaDecisionLog> page) {
		RtaEndpoint endpoint = endpointMapper.selectById(id);
		if (endpoint == null || !mediaId.equals(endpoint.getMediaId()) || !scopeService.canRead(mediaId)) {
			return Result.fail("资源不存在");
		}
		return Result.success(logMapper.selectPage(page,
				Wrappers.<RtaDecisionLog>lambdaQuery()
					.eq(RtaDecisionLog::getEndpointId, id)
					.orderByDesc(RtaDecisionLog::getRequestedAt)));
	}

}
