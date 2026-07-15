package com.yzytech.scx.subject.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.subject.service.MediaReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/subjects/media")
@Tag(name = "Media", description = "媒体主体与对象级授权")
public class MediaController {

	private final MediaReadService mediaReadService;

	@GetMapping
	@Operation(summary = "查询当前用户可访问的媒体主体")
	@SaCheckPermission("subject:media:read")
	public Result list() {
		return Result.success(mediaReadService.listForCurrentUser());
	}

	@GetMapping("/{mediaId}")
	@Operation(summary = "查询一个已授权媒体主体")
	@SaCheckPermission("subject:media:read")
	public Result get(@PathVariable String mediaId) {
		return mediaReadService.getForCurrentUser(mediaId)
			.map(Result::success)
			.orElseGet(() -> Result.fail("媒体主体不存在或无访问权限"));
	}
}
