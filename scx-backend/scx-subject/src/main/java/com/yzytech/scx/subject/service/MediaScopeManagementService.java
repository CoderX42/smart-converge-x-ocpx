package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.subject.dto.MediaScopeGrantRequest;
import com.yzytech.scx.subject.dto.MediaScopeUserOption;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.mapper.MediaMapper;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MediaScopeManagementService {

	private static final String MEDIA = "MEDIA";

	private final UserSubjectScopeMapper scopeMapper;

	private final MediaMapper mediaMapper;

	private final MediaScopeService mediaScopeService;

	public Page<UserSubjectScope> page(Page<UserSubjectScope> page, String mediaId, String userId) {
		if (page == null) {
			page = new Page<>();
		}
		if (StringUtils.hasText(mediaId)) {
			requireManageableMedia(mediaId);
		}
		else if (!mediaScopeService.isAdmin()) {
			throw new IllegalArgumentException("资源不存在");
		}
		return scopeMapper.selectPage(page,
				Wrappers.<UserSubjectScope>lambdaQuery()
					.eq(UserSubjectScope::getSubjectType, MEDIA)
					.eq(StringUtils.hasText(mediaId), UserSubjectScope::getSubjectId, mediaId)
					.eq(StringUtils.hasText(userId), UserSubjectScope::getUserId, userId)
					.orderByDesc(UserSubjectScope::getCreateTime));
	}

	@Transactional
	public UserSubjectScope grant(MediaScopeGrantRequest request) {
		if (request == null || !StringUtils.hasText(request.getUserId())
				|| !StringUtils.hasText(request.getMediaId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		requireManageableMedia(request.getMediaId());
		if (scopeMapper.countActiveUser(request.getUserId()) != 1) {
			throw new IllegalArgumentException("用户不存在或已停用");
		}
		UserSubjectScope existing = scopeMapper.selectOne(Wrappers.<UserSubjectScope>lambdaQuery()
			.eq(UserSubjectScope::getUserId, request.getUserId())
			.eq(UserSubjectScope::getSubjectType, MEDIA)
			.eq(UserSubjectScope::getSubjectId, request.getMediaId()));
		if (existing != null) {
			return existing;
		}
		UserSubjectScope scope = new UserSubjectScope();
		scope.setUserId(request.getUserId());
		scope.setSubjectType(MEDIA);
		scope.setSubjectId(request.getMediaId());
		try {
			scopeMapper.insert(scope);
			return scope;
		}
		catch (DuplicateKeyException duplicate) {
			// The unique (user_id, subject_type, subject_id) key makes a
			// concurrent grant safe. Re-read and return the already committed
			// scope instead of exposing a 500 to an idempotent caller.
			UserSubjectScope raced = scopeMapper.selectOne(Wrappers.<UserSubjectScope>lambdaQuery()
				.eq(UserSubjectScope::getUserId, request.getUserId())
				.eq(UserSubjectScope::getSubjectType, MEDIA)
				.eq(UserSubjectScope::getSubjectId, request.getMediaId()));
			if (raced != null) {
				return raced;
			}
			throw duplicate;
		}
	}

	@Transactional
	public boolean revoke(String id, String mediaId) {
		if (!StringUtils.hasText(id) || !StringUtils.hasText(mediaId)) {
			throw new IllegalArgumentException("资源不存在");
		}
		requireManageableMedia(mediaId);
		UserSubjectScope existing = scopeMapper.selectById(id);
		if (existing == null || !MEDIA.equals(existing.getSubjectType()) || !mediaId.equals(existing.getSubjectId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		if (!mediaScopeService.isAdmin() && existing.getUserId().equals(mediaScopeService.currentUserId())) {
			throw new IllegalStateException("不能撤销自己的媒体范围");
		}
		return scopeMapper.deleteById(id) == 1;
	}

	public Page<MediaScopeUserOption> candidates(Page<MediaScopeUserOption> page, String mediaId, String keyword) {
		if (!StringUtils.hasText(mediaId)) {
			throw new IllegalArgumentException("资源不存在");
		}
		requireManageableMedia(mediaId);
		Page<MediaScopeUserOption> request = page == null ? new Page<>() : page;
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		return scopeMapper.selectCandidatePage(request, normalizedKeyword);
	}

	private void requireManageableMedia(String mediaId) {
		Media media = mediaMapper.selectById(mediaId);
		if (media == null || !"ACTIVE".equals(media.getStatus()) || !mediaScopeService.canWrite(mediaId)) {
			// Keep the same error for absent, disabled and out-of-scope media.
			throw new IllegalArgumentException("资源不存在");
		}
	}

}
