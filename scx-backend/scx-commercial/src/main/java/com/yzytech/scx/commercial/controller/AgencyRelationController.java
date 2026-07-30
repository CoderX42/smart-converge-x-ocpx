package com.yzytech.scx.commercial.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.commercial.dto.AccountTreeView;
import com.yzytech.scx.commercial.dto.CreateAgencyRelationRequest;
import com.yzytech.scx.commercial.entity.AgencyRelation;
import com.yzytech.scx.commercial.service.CommercialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/commercial")
@Tag(name = "代理关系管理", description = "代理关系创建、撤销与账户树查询")
public class AgencyRelationController {

	private final CommercialService commercialService;

	@PostMapping("/agency-relations")
	@SaCheckPermission("commercial:agency:write")
	@Operation(summary = "创建代理关系")
	public Result<AgencyRelation> create(@Valid @RequestBody CreateAgencyRelationRequest req) {
		return call(() -> commercialService.createAgencyRelation(req));
	}

	@GetMapping("/agency-relations")
	@SaCheckPermission("commercial:agency:read")
	@Operation(summary = "查询代理关系列表")
	public Result<List<AgencyRelation>> list(@RequestParam(required = false) String parentOrgId) {
		return call(() -> commercialService.listAgencyRelations(parentOrgId));
	}

	@GetMapping("/agency-relations/page")
	@SaCheckPermission("commercial:agency:read")
	@Operation(summary = "分页查询代理关系")
	public Result<Page<AgencyRelation>> page(Page<AgencyRelation> page,
			@RequestParam(required = false) String parentOrgId, @RequestParam(required = false) String status) {
		return call(() -> commercialService.pageAgencyRelations(page, parentOrgId, status));
	}

	@PutMapping("/agency-relations/{id}")
	@SaCheckPermission("commercial:agency:write")
	@Operation(summary = "更新代理关系")
	public Result<Boolean> update(@PathVariable String id, @Valid @RequestBody CreateAgencyRelationRequest req) {
		return call(() -> {
			commercialService.updateAgencyRelation(id, req);
			return true;
		});
	}

	@DeleteMapping("/agency-relations/{id}")
	@SaCheckPermission("commercial:agency:write")
	@Operation(summary = "撤销代理关系")
	public Result<Boolean> revoke(@PathVariable String id) {
		return call(() -> {
			commercialService.revokeAgencyRelation(id);
			return true;
		});
	}

	@GetMapping("/account-tree")
	@SaCheckPermission("commercial:agency:read")
	@Operation(summary = "查询账户树（组织层级 + 广告账户）")
	public Result<AccountTreeView> accountTree(@RequestParam(required = false, defaultValue = "1") String rootOrgId) {
		return call(() -> commercialService.buildAccountTree(rootOrgId));
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
