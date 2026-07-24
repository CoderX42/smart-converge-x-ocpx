package com.yzytech.scx.subject.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.mapper.MediaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/subjects/media")
public class MediaController {

	private final MediaMapper mediaMapper;

	@GetMapping
	@SaCheckPermission("subject:media:read")
	public Result<Page<Media>> page(Page<Media> page, String keyword) {
		var query = Wrappers.<Media>lambdaQuery()
			.eq(Media::getStatus, "ACTIVE")
			.and(StringUtils.hasText(keyword), q -> q.like(Media::getCode, keyword).or().like(Media::getName, keyword));
		return Result.success(mediaMapper.selectPage(page, query.orderByAsc(Media::getName)));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("subject:media:read")
	public Result<Media> detail(@PathVariable String id) {
		Media media = mediaMapper.selectById(id);
		return media == null || !"ACTIVE".equals(media.getStatus()) ? Result.fail(404, "资源不存在") : Result.success(media);
	}

}
