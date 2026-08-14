package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.ocpx.admin.dto.MediaRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaViews;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.admin.service.OcpxMediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/media")
public class OcpxMediaController {

	private final OcpxMediaService mediaService;

	@GetMapping
	@SaCheckPermission("ocpx:media:view")
	public Result<IPage<MediaViews.Item>> list(Page<OcpxMedia> page, String keyword, String mediaId) {
		return Result.success(mediaService.list(page, keyword, mediaId));
	}

	@GetMapping("/options")
	@SaCheckPermission(
			value = { "ocpx:media:view", "ocpx:monitor-task:view", "ocpx:report:view", "ocpx:dashboard:view" },
			mode = SaMode.OR)
	public Result<List<MediaViews.Option>> options() {
		return Result.success(mediaService.options());
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:media:view")
	public Result<MediaViews.Item> detail(@PathVariable String id) {
		return Result.success(mediaService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:media:add")
	@SysLog("新增媒体")
	public Result<MediaViews.Item> create(@Valid @RequestBody MediaRequests.Create request) {
		return Result.success(mediaService.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:media:edit")
	@SysLog("修改媒体")
	public Result<MediaViews.Item> update(@PathVariable String id, @Valid @RequestBody MediaRequests.Update request) {
		return Result.success(mediaService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@SaCheckPermission("ocpx:media:delete")
	@SysLog("删除媒体")
	public Result<Void> delete(@PathVariable String id) {
		mediaService.delete(id);
		return Result.success();
	}

}
