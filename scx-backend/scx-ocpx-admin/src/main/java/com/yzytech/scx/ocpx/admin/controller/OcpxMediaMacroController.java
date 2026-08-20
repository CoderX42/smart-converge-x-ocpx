package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.service.MediaMacroCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/media-macros")
public class OcpxMediaMacroController {

	private final MediaMacroCatalog catalog;

	@GetMapping
	@SaCheckPermission("ocpx:media:view")
	public Result<List<MediaMacroCatalog.Macro>> list() {
		return Result.success(catalog.list());
	}

}
