package com.yzytech.scx.commercial.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.commercial.dto.SaveOrganizationRequest;
import com.yzytech.scx.commercial.entity.Organization;
import com.yzytech.scx.commercial.service.CommercialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/commercial/organizations")
public class OrganizationController {

	private final CommercialService service;

	@PostMapping
	@SaCheckPermission("commercial:organization:write")
	public Result<Organization> create(@Valid @RequestBody SaveOrganizationRequest request) {
		return call(() -> service.createOrganization(request.name(), request.orgType(), request.parentOrgId(),
				request.contactName(), request.contactPhone(), request.contactEmail()));
	}

	@GetMapping
	@SaCheckPermission("commercial:organization:read")
	public Result<Page<Organization>> page(Page<Organization> page, String orgType, String keyword) {
		return Result.success(service.listOrganizations(page, orgType, keyword));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("commercial:organization:read")
	public Result<Organization> get(@PathVariable String id) {
		try {
			return Result.success(service.requireOrg(id));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(404, "资源不存在");
		}
	}

	@PutMapping("/{id}")
	@SaCheckPermission("commercial:organization:write")
	public Result<Organization> update(@PathVariable String id, @Valid @RequestBody SaveOrganizationRequest request) {
		return call(() -> service.updateOrganization(id, request.name(), request.orgType(), request.parentOrgId(),
				request.legalPerson(), request.contactName(), request.contactPhone(), request.contactEmail()));
	}

	@PutMapping("/{id}/status")
	@SaCheckPermission("commercial:organization:write")
	public Result<Organization> updateStatus(@PathVariable String id, @RequestParam String status) {
		return call(() -> service.updateOrganizationStatus(id, status));
	}

	@DeleteMapping("/{id}")
	@SaCheckPermission("commercial:organization:write")
	public Result<Boolean> delete(@PathVariable String id) {
		return call(() -> {
			service.archiveOrganization(id);
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
