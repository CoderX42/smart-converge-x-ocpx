package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.ocpx.admin.dto.CreativeRequests;
import com.yzytech.scx.ocpx.admin.dto.CreativeViews;
import com.yzytech.scx.ocpx.admin.service.OcpxCreativeService;
import com.yzytech.scx.ocpx.entity.OcpxCreative;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/creatives")
public class OcpxCreativeController {

	private final OcpxCreativeService service;

	@GetMapping
	@SaCheckPermission("ocpx:creative:view")
	public Result<IPage<CreativeViews.Item>> list(Page<OcpxCreative> page, String keyword, String advertiserId,
			Integer status, String promotionLinkId) {
		return Result.success(service.list(page, keyword, advertiserId, status, promotionLinkId));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:creative:view")
	public Result<CreativeViews.Item> detail(@PathVariable String id) {
		return Result.success(service.detail(id));
	}

	@GetMapping("/assets")
	@SaCheckPermission("ocpx:creative:view")
	public Result<List<CreativeViews.Asset>> assets(String advertiserId, String keyword) {
		return Result.success(service.assets(advertiserId, keyword));
	}

	@PostMapping(value = "/assets", consumes = "multipart/form-data")
	@SaCheckPermission("ocpx:creative:add")
	@SysLog("上传创意素材")
	public Result<CreativeViews.UploadedAsset> upload(@RequestParam String advertiserId,
			@RequestPart("file") MultipartFile file) {
		return Result.success(service.upload(advertiserId, file));
	}

	@PostMapping
	@SaCheckPermission({ "ocpx:creative:add", "ocpx:creative:bind" })
	@SysLog("新增创意")
	public Result<CreativeViews.Item> create(@Valid @RequestBody CreativeRequests.Save request) {
		return Result.success(service.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission({ "ocpx:creative:edit", "ocpx:creative:bind" })
	@SysLog("修改创意")
	public Result<CreativeViews.Item> update(@PathVariable String id,
			@Valid @RequestBody CreativeRequests.Save request) {
		return Result.success(service.update(id, request));
	}

	@GetMapping("/{id}/promotion-links")
	@SaCheckPermission("ocpx:creative:view")
	public Result<List<CreativeViews.LinkOption>> bindings(@PathVariable String id) {
		return Result.success(service.bindings(id));
	}

	@PutMapping("/{id}/promotion-links")
	@SaCheckPermission("ocpx:creative:bind")
	@SysLog("绑定创意推广链接")
	public Result<List<CreativeViews.LinkOption>> replaceBindings(@PathVariable String id,
			@Valid @RequestBody CreativeRequests.Bindings request) {
		return Result.success(service.replaceBindings(id, request));
	}

	@DeleteMapping("/{id}")
	@SaCheckPermission("ocpx:creative:delete")
	@SysLog("删除创意")
	public Result<Void> delete(@PathVariable String id) {
		service.delete(id);
		return Result.success();
	}

}
