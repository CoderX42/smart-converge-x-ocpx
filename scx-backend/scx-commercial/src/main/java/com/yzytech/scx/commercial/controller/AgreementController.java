package com.yzytech.scx.commercial.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.commercial.dto.CreateAgreementInstanceRequest;
import com.yzytech.scx.commercial.dto.CreateAgreementTemplateRequest;
import com.yzytech.scx.commercial.dto.CreateDataAuthorizationRequest;
import com.yzytech.scx.commercial.entity.AgreementInstance;
import com.yzytech.scx.commercial.entity.AgreementTemplate;
import com.yzytech.scx.commercial.entity.DataAuthorization;
import com.yzytech.scx.commercial.port.PublishingEligibilityPort;
import com.yzytech.scx.commercial.service.AgreementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/commercial")
public class AgreementController {

	private final AgreementService agreementService;

	private final PublishingEligibilityPort eligibilityPort;

	@PostMapping("/agreement-templates")
	@SaCheckPermission("commercial:agreement:write")
	public Result<AgreementTemplate> createTemplate(@Valid @RequestBody CreateAgreementTemplateRequest request) {
		return call(() -> agreementService.createTemplate(request));
	}

	@GetMapping("/agreement-templates")
	@SaCheckPermission("commercial:agreement:read")
	public Result<Page<AgreementTemplate>> pageTemplates(Page<AgreementTemplate> page, String templateType,
			String status) {
		return call(() -> agreementService.pageTemplates(page, templateType, status));
	}

	@PutMapping("/agreement-templates/{id}")
	@SaCheckPermission("commercial:agreement:write")
	public Result<AgreementTemplate> updateTemplate(@PathVariable String id,
			@Valid @RequestBody CreateAgreementTemplateRequest request) {
		return call(() -> agreementService.updateTemplate(id, request));
	}

	@DeleteMapping("/agreement-templates/{id}")
	@SaCheckPermission("commercial:agreement:write")
	public Result<Boolean> archiveTemplate(@PathVariable String id) {
		return call(() -> {
			agreementService.archiveTemplate(id);
			return true;
		});
	}

	@PostMapping("/agreement-templates/{id}/activate")
	@SaCheckPermission("commercial:agreement:write")
	public Result<AgreementTemplate> activateTemplate(@PathVariable String id) {
		return call(() -> agreementService.activateTemplate(id));
	}

	@PostMapping("/agreement-instances")
	@SaCheckPermission("commercial:agreement:write")
	public Result<AgreementInstance> createInstance(@Valid @RequestBody CreateAgreementInstanceRequest request) {
		return call(() -> agreementService.createInstance(request));
	}

	@PutMapping("/agreement-instances/{id}")
	@SaCheckPermission("commercial:agreement:write")
	public Result<AgreementInstance> updateInstance(@PathVariable String id,
			@Valid @RequestBody CreateAgreementInstanceRequest request) {
		return call(() -> agreementService.updateInstance(id, request));
	}

	@DeleteMapping("/agreement-instances/{id}")
	@SaCheckPermission("commercial:agreement:write")
	public Result<Boolean> deleteInstance(@PathVariable String id) {
		return call(() -> {
			agreementService.cancelInstance(id);
			return true;
		});
	}

	@PostMapping("/agreement-instances/{id}/sign")
	@SaCheckPermission("commercial:agreement:write")
	public Result<AgreementInstance> sign(@PathVariable String id) {
		return call(() -> agreementService.sign(id));
	}

	@PostMapping("/agreement-instances/{id}/terminate")
	@SaCheckPermission("commercial:agreement:write")
	public Result<AgreementInstance> terminate(@PathVariable String id) {
		return call(() -> agreementService.terminate(id));
	}

	@GetMapping("/agreement-instances")
	@SaCheckPermission("commercial:agreement:read")
	public Result<Page<AgreementInstance>> pageInstances(Page<AgreementInstance> page, String organizationId) {
		return call(() -> agreementService.pageInstances(page, organizationId));
	}

	@GetMapping("/agreement-instances/{id}")
	@SaCheckPermission("commercial:agreement:read")
	public Result<AgreementInstance> getInstance(@PathVariable String id) {
		return call(() -> agreementService.getInstance(id));
	}

	@PostMapping("/data-authorizations")
	@SaCheckPermission("commercial:data-authorization:write")
	public Result<DataAuthorization> createAuthorization(@Valid @RequestBody CreateDataAuthorizationRequest request) {
		return call(() -> agreementService.createAuthorization(request));
	}

	@PutMapping("/data-authorizations/{id}")
	@SaCheckPermission("commercial:data-authorization:write")
	public Result<DataAuthorization> updateAuthorization(@PathVariable String id,
			@Valid @RequestBody CreateDataAuthorizationRequest request) {
		return call(() -> agreementService.updateAuthorization(id, request));
	}

	@GetMapping("/data-authorizations")
	@SaCheckPermission("commercial:data-authorization:read")
	public Result<Page<DataAuthorization>> pageAuthorizations(Page<DataAuthorization> page, String organizationId,
			String status) {
		return call(() -> agreementService.pageAuthorizations(page, organizationId, status));
	}

	@PostMapping("/data-authorizations/{id}/revoke")
	@SaCheckPermission("commercial:data-authorization:write")
	public Result<DataAuthorization> revokeAuthorization(@PathVariable String id) {
		return call(() -> agreementService.revokeAuthorization(id));
	}

	@DeleteMapping("/data-authorizations/{id}")
	@SaCheckPermission("commercial:data-authorization:write")
	public Result<Boolean> deleteAuthorization(@PathVariable String id) {
		return call(() -> {
			agreementService.revokeAuthorization(id);
			return true;
		});
	}

	@GetMapping("/ad-accounts/{id}/publishing-eligibility")
	@SaCheckPermission("commercial:ad-account:read")
	public Result<PublishingEligibilityPort.PublishingEligibility> publishingEligibility(@PathVariable String id) {
		return call(() -> eligibilityPort.evaluate(id));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(404, "资源不存在");
		}
		catch (Exception e) {
			return Result.fail("商业协议操作失败");
		}
	}

}
