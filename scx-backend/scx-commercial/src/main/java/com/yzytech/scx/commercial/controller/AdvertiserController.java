package com.yzytech.scx.commercial.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.commercial.dto.CreateAdvertiserRequest;
import com.yzytech.scx.commercial.dto.UpdateAdvertiserRequest;
import com.yzytech.scx.commercial.entity.Advertiser;
import com.yzytech.scx.commercial.service.CommercialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/commercial")
@Tag(name = "广告主管理", description = "广告主创建、查询与状态管理")
public class AdvertiserController {

	private final CommercialService commercialService;

	@PostMapping("/advertisers")
	@SaCheckPermission("commercial:advertiser:write")
	@Operation(summary = "创建广告主")
	public Result<Advertiser> create(@Valid @RequestBody CreateAdvertiserRequest req) {
		return call(() -> commercialService.createAdvertiser(req));
	}

	@GetMapping("/advertisers")
	@SaCheckPermission("commercial:advertiser:read")
	@Operation(summary = "查询广告主列表")
	public Result<Page<Advertiser>> list(Page<Advertiser> page, String status, String keyword) {
		return call(() -> commercialService.listAdvertisers(page, status, keyword));
	}

	@GetMapping("/advertisers/{id}")
	@SaCheckPermission("commercial:advertiser:read")
	@Operation(summary = "查询单个广告主")
	public Result<Advertiser> get(@PathVariable String id) {
		return call(() -> commercialService.requireAdvertiser(id));
	}

	@PutMapping("/advertisers/{id}/status")
	@SaCheckPermission("commercial:advertiser:write")
	@Operation(summary = "更新广告主状态")
	public Result<Boolean> updateStatus(@PathVariable String id, @RequestParam String status) {
		return call(() -> {
			commercialService.updateAdvertiserStatus(id, status);
			return true;
		});
	}

	@PutMapping("/advertisers/{id}")
	@SaCheckPermission("commercial:advertiser:write")
	@Operation(summary = "更新广告主")
	public Result<Advertiser> update(@PathVariable String id, @Valid @RequestBody UpdateAdvertiserRequest req) {
		return call(() -> commercialService.updateAdvertiserProfile(id, req));
	}

	@DeleteMapping("/advertisers/{id}")
	@SaCheckPermission("commercial:advertiser:write")
	@Operation(summary = "归档广告主")
	public Result<Boolean> delete(@PathVariable String id) {
		return call(() -> {
			commercialService.archiveAdvertiser(id);
			return true;
		});
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("操作失败");
		}
	}

}
