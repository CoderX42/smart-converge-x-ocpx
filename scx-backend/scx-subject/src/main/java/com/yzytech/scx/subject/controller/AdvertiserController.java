package com.yzytech.scx.subject.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.subject.entity.Advertiser;
import com.yzytech.scx.subject.service.AdvertiserReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/subjects/advertisers")
@Tag(name = "Advertiser", description = "广告主体与对象级授权")
public class AdvertiserController {

	private final AdvertiserReadService advertiserReadService;

	@GetMapping
	@Operation(summary = "查询当前用户可访问的广告主体")
	@SaCheckPermission("subject:advertiser:read")
	public Result list() {
		return Result.success(advertiserReadService.listForCurrentUser());
	}

	@GetMapping("/{advertiserId}")
	@Operation(summary = "查询一个已授权广告主体")
	@SaCheckPermission("subject:advertiser:read")
	public ResponseEntity<Result<Advertiser>> get(@PathVariable String advertiserId) {
		return advertiserReadService.getForCurrentUser(advertiserId)
			.map(advertiser -> ResponseEntity.ok(Result.success(advertiser)))
			.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Result.fail(HttpStatus.NOT_FOUND.value(), "广告主体不存在或无访问权限")));
	}
}
