package com.yzytech.scx.ocpx.admin.controller;

import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.ocpx.admin.dto.BusinessCodeRequests;
import com.yzytech.scx.ocpx.admin.dto.BusinessCodeViews;
import com.yzytech.scx.ocpx.admin.service.OcpxBusinessCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ocpx/business-codes")
public class OcpxBusinessCodeController {

	private final OcpxBusinessCodeService businessCodeService;

	@PostMapping("/generate")
	public Result<BusinessCodeViews.Generated> generate(@Valid @RequestBody BusinessCodeRequests.Generate request) {
		return Result.success(businessCodeService.generate(request.resourceType()));
	}

}
