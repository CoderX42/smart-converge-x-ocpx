package com.yzytech.scx.subject.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.subject.dto.MediaCreateRequest;
import com.yzytech.scx.subject.dto.MediaStatusRequest;
import com.yzytech.scx.subject.dto.MediaUpdateRequest;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.mapper.MediaMapper;
import com.yzytech.scx.subject.service.MediaManagementService;
import com.yzytech.scx.subject.service.MediaScopeService;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Media subject controller.
 * <p>
 * Object-level media isolation is enforced through {@link MediaScopeService}:
 * administrators see every active media; other users only see media explicitly granted
 * via {@code scx_user_subject_scope}. The detail endpoint returns the same 404 response
 * for "no scope" and "does not exist" so unauthorized media ids cannot be enumerated.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/subjects/media")
public class MediaController {

	private final MediaMapper mediaMapper;

	private final MediaScopeService mediaScopeService;

	private final MediaManagementService mediaManagementService;

	@GetMapping
	@SaCheckPermission("subject:media:read")
	public Result<Page<Media>> page(Page<Media> page, String keyword) {
		if (page == null) {
			page = new Page<>();
		}
		var query = Wrappers.<Media>lambdaQuery().eq(Media::getStatus, "ACTIVE");
		if (mediaScopeService.isAdmin()) {
			query.and(StringUtils.hasText(keyword),
					q -> q.like(Media::getCode, keyword).or().like(Media::getName, keyword));
		}
		else {
			List<String> allowedIds = mediaScopeService.readableMediaIds();
			if (allowedIds.isEmpty()) {
				return Result.success(page.setRecords(Collections.emptyList()).setTotal(0));
			}
			query.in(Media::getId, allowedIds);
			if (StringUtils.hasText(keyword)) {
				query.and(q -> q.like(Media::getCode, keyword).or().like(Media::getName, keyword));
			}
		}
		return Result.success(mediaMapper.selectPage(page, query.orderByAsc(Media::getName)));
	}

	@GetMapping("/manage")
	@SaCheckPermission("subject:media:write")
	public Result<Page<Media>> manage(Page<Media> page, String keyword, String status) {
		return call(() -> mediaManagementService.page(page, keyword, status));
	}

	@PostMapping
	@SysLog("新增媒体")
	@SaCheckPermission("subject:media:write")
	public Result<Media> create(@Valid @RequestBody MediaCreateRequest request) {
		return call(() -> mediaManagementService.create(request));
	}

	@PutMapping("/{id}")
	@SysLog("修改媒体")
	@SaCheckPermission("subject:media:write")
	public Result<Media> update(@PathVariable String id, @Valid @RequestBody MediaUpdateRequest request) {
		return call(() -> mediaManagementService.update(id, request));
	}

	@PutMapping("/{id}/status")
	@SysLog("变更媒体状态")
	@SaCheckPermission("subject:media:write")
	public Result<Media> changeStatus(@PathVariable String id, @Valid @RequestBody MediaStatusRequest request) {
		return call(() -> mediaManagementService.changeStatus(id, request));
	}

	@DeleteMapping("/{id}")
	@SysLog("删除媒体")
	@SaCheckPermission("subject:media:write")
	public Result<Boolean> delete(@PathVariable String id, @RequestParam Integer version) {
		return call(() -> mediaManagementService.delete(id, version));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("subject:media:read")
	public Result<Media> detail(@PathVariable String id) {
		Media media = mediaMapper.selectById(id);
		if (media == null || !"ACTIVE".equals(media.getStatus())) {
			return Result.fail(404, "资源不存在");
		}
		if (!mediaScopeService.canRead(media.getId())) {
			return Result.fail(404, "资源不存在");
		}
		return Result.success(media);
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return Result.fail(409, e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("媒体操作失败");
		}
	}

}
