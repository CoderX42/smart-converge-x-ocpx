package com.yzytech.scx.tracking.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.tracking.dto.ChannelClientCreateRequest;
import com.yzytech.scx.tracking.dto.ChannelClientUpdateRequest;
import com.yzytech.scx.tracking.dto.ChannelCredentialIssueResponse;
import com.yzytech.scx.tracking.dto.ChannelCredentialView;
import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.tracking.service.ChannelClientManagementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/channel-clients")
public class ChannelClientController {

	private final ChannelClientManagementService service;

	@GetMapping
	@SaCheckPermission("ad:channel-client:read")
	public Result<Page<ChannelClient>> page(Page<ChannelClient> page, @RequestParam String mediaId, String keyword,
			String status) {
		return call(() -> service.page(page, mediaId, keyword, status));
	}

	@GetMapping("/{id}/credentials")
	@SaCheckPermission("ad:channel-client:read")
	public Result<List<ChannelCredentialView>> credentials(@PathVariable String id, @RequestParam String mediaId) {
		return call(() -> service.credentials(id, mediaId));
	}

	@PostMapping
	@SaCheckPermission("ad:channel-client:write")
	@SysLog("创建 OCPX 渠道凭据")
	public Result<ChannelCredentialIssueResponse> create(@Valid @RequestBody ChannelClientCreateRequest request) {
		return call(() -> service.create(request));
	}

	@PutMapping("/{id}")
	@SaCheckPermission("ad:channel-client:write")
	@SysLog("修改 OCPX 渠道客户端")
	public Result<Boolean> update(@PathVariable String id, @Valid @RequestBody ChannelClientUpdateRequest request) {
		return call(() -> service.update(id, request));
	}

	@PostMapping("/{id}/credentials/rotate")
	@SaCheckPermission("ad:channel-client:rotate")
	@SysLog("轮换 OCPX 渠道密钥")
	public Result<ChannelCredentialIssueResponse> rotate(@PathVariable String id, @RequestParam String mediaId) {
		return call(() -> service.rotate(id, mediaId));
	}

	@PostMapping("/{id}/credentials/{keyVersion}/revoke")
	@SaCheckPermission("ad:channel-client:rotate")
	@SysLog("吊销 OCPX 渠道密钥")
	public Result<Boolean> revoke(@PathVariable String id, @PathVariable String keyVersion,
			@RequestParam String mediaId) {
		return call(() -> service.revoke(id, keyVersion, mediaId));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException | IllegalStateException exception) {
			return "资源不存在".equals(exception.getMessage()) ? Result.fail(404, "资源不存在")
					: Result.fail(exception.getMessage());
		}
		catch (Exception exception) {
			return Result.fail("渠道凭据操作失败");
		}
	}

}
