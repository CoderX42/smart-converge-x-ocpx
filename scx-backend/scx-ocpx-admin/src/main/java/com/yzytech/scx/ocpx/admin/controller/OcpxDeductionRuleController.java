package com.yzytech.scx.ocpx.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.DeductionRuleRequests;
import com.yzytech.scx.ocpx.admin.dto.DeductionRuleViews;
import com.yzytech.scx.ocpx.entity.OcpxDeductionRule;
import com.yzytech.scx.ocpx.admin.service.OcpxDeductionRuleService;
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

import java.util.concurrent.Callable;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/deduction-rules")
public class OcpxDeductionRuleController {

	private final OcpxDeductionRuleService deductionRuleService;

	@GetMapping
	@SaCheckPermission("ocpx:deduction-rule:list")
	public Result<IPage<DeductionRuleViews.Item>> list(Page<OcpxDeductionRule> page, String promotionLinkId) {
		return Result.success(deductionRuleService.list(page, promotionLinkId));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("ocpx:deduction-rule:list")
	public Result<DeductionRuleViews.Item> detail(@PathVariable String id) {
		return call(() -> deductionRuleService.detail(id));
	}

	@PostMapping
	@SaCheckPermission("ocpx:deduction-rule:create")
	@SysLog("新增扣量规则")
	public Result<DeductionRuleViews.Item> create(@Valid @RequestBody DeductionRuleRequests.Create request) {
		return call(() -> deductionRuleService.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ocpx:deduction-rule:update")
	@SysLog("修改扣量规则")
	public Result<DeductionRuleViews.Item> update(@PathVariable String id,
			@Valid @RequestBody DeductionRuleRequests.Update request) {
		return call(() -> deductionRuleService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@SaCheckPermission("ocpx:deduction-rule:delete")
	@SysLog("删除扣量规则")
	public Result<Void> delete(@PathVariable String id) {
		return call(() -> {
			deductionRuleService.delete(id);
			return null;
		});
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
