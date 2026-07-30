package com.yzytech.scx.commercial.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.commercial.dto.CreateAdAccountRequest;
import com.yzytech.scx.commercial.dto.UpdateAdAccountRequest;
import com.yzytech.scx.commercial.entity.AdAccount;
import com.yzytech.scx.commercial.service.CommercialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/commercial")
@Tag(name = "广告账户管理", description = "广告账户创建、查询与状态管理")
public class AdAccountController {

	private final CommercialService commercialService;

	@PostMapping("/ad-accounts")
	@SaCheckPermission("commercial:ad-account:write")
	@Operation(summary = "创建广告账户")
	public Result<AdAccount> create(@Valid @RequestBody CreateAdAccountRequest req) {
		return call(() -> commercialService.createAdAccount(req));
	}

	@GetMapping("/ad-accounts")
	@SaCheckPermission("commercial:ad-account:read")
	@Operation(summary = "查询广告账户列表")
	public Result<Page<AdAccount>> list(Page<AdAccount> page, String advertiserOrgId, String status, String keyword) {
		return call(() -> commercialService.listAdAccounts(page, advertiserOrgId, status, keyword));
	}

	@GetMapping("/ad-accounts/{id}")
	@SaCheckPermission("commercial:ad-account:read")
	@Operation(summary = "查询单个广告账户")
	public Result<AdAccount> get(@PathVariable String id) {
		return call(() -> commercialService.requireAdAccount(id));
	}

	@PutMapping("/ad-accounts/{id}/status")
	@SaCheckPermission("commercial:ad-account:write")
	@Operation(summary = "更新广告账户状态")
	public Result<Boolean> updateStatus(@PathVariable String id, @RequestParam String status) {
		return call(() -> {
			commercialService.updateAdAccountStatus(id, status);
			return true;
		});
	}

	@PutMapping("/ad-accounts/{id}")
	@SaCheckPermission("commercial:ad-account:write")
	@Operation(summary = "更新广告账户")
	public Result<AdAccount> update(@PathVariable String id, @Valid @RequestBody UpdateAdAccountRequest req) {
		return call(() -> commercialService.updateAdAccount(id, req));
	}

	@DeleteMapping("/ad-accounts/{id}")
	@SaCheckPermission("commercial:ad-account:write")
	@Operation(summary = "关闭广告账户")
	public Result<Boolean> delete(@PathVariable String id) {
		return call(() -> {
			commercialService.closeAdAccount(id);
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
