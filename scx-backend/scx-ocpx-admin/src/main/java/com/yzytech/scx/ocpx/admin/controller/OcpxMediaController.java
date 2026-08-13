package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.MediaRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaViews;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.admin.service.OcpxMediaService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/media")
public class OcpxMediaController {

	private final OcpxMediaService mediaService;

	@GetMapping
	@SaCheckPermission("ocpx:media:view")
	public Result<IPage<MediaViews.Item>> list(Page<OcpxMedia> page, String keyword, String adapterCode,
			String status) {
		return Result.success(mediaService.list(page, keyword, adapterCode, status));
	}

	@GetMapping(":export")
	@SaCheckPermission("ocpx:media:view")
	public void export(String keyword, String adapterCode, String status, HttpServletResponse response) {
		try {
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.setContentType("text/csv;charset=UTF-8");
			String fileName = URLEncoder.encode("媒体管理报表", StandardCharsets.UTF_8).replace("+", "%20");
			response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".csv");
			response.getWriter().print("\ufeff" + mediaService.exportCsv(keyword, adapterCode, status));
		}
		catch (Exception error) {
			log.error("Export csv error!!!", error);
		}
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:media:view")
	public Result<MediaViews.Item> detail(@PathVariable String id) {
		return call(() -> mediaService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:media:add")
	@SysLog("新增媒体")
	public Result<MediaViews.Item> create(@Valid @RequestBody MediaRequests.Create request) {
		return call(() -> mediaService.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:media:edit")
	@SysLog("修改媒体")
	public Result<MediaViews.Item> update(@PathVariable String id, @Valid @RequestBody MediaRequests.Update request) {
		return call(() -> mediaService.update(id, request));
	}

	@PostMapping("/{id}/status")
	@SaCheckPermission("ocpx:media:status")
	@SysLog("修改媒体状态")
	public Result<MediaViews.Mutation> status(@PathVariable String id,
			@Valid @RequestBody MediaRequests.StatusAction request) {
		return call(() -> mediaService.changeStatus(id, request));
	}

	@PostMapping("/{id}/archive")
	@SaCheckPermission("ocpx:media:archive")
	@SysLog("归档媒体")
	public Result<MediaViews.Mutation> archive(@PathVariable String id,
			@Valid @RequestBody MediaRequests.VersionedAction request) {
		return call(() -> mediaService.archive(id, request));
	}

	@PostMapping("/{id}/preflight")
	@SaCheckPermission("ocpx:media:preflight")
	public Result<MediaViews.Preflight> preflight(@PathVariable String id) {
		return call(() -> mediaService.preflight(id));
	}

	@PostMapping("/{id}/connectivity-check")
	@SaCheckPermission("ocpx:media:connectivity")
	public Result<MediaViews.Connectivity> connectivity(@PathVariable String id) {
		return call(() -> mediaService.connectivity(id));
	}

	private <T> Result<T> call(Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException error) {
			return Result.fail(error.getMessage());
		}
		catch (BootBusinessException error) {
			throw error;
		}
		catch (Exception error) {
			return Result.fail("操作失败");
		}
	}

}
