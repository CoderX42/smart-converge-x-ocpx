package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.subject.dto.MediaScopeGrantRequest;
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

	public Page<UserSubjectScope> page(Page<UserSubjectScope> page, String mediaId, String userId) {
		if (StringUtils.hasText(mediaId)) {
			requireActiveMedia(mediaId);
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
		Media media = mediaMapper.selectById(request.getMediaId());
		if (media == null || !"ACTIVE".equals(media.getStatus())) {
			throw new IllegalArgumentException("资源不存在");
		}
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
		UserSubjectScope existing = scopeMapper.selectById(id);
		if (existing == null || !MEDIA.equals(existing.getSubjectType()) || !StringUtils.hasText(mediaId)
				|| !mediaId.equals(existing.getSubjectId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		return scopeMapper.deleteById(id) == 1;
	}

	private void requireActiveMedia(String mediaId) {
		Media media = mediaMapper.selectById(mediaId);
		if (media == null || !"ACTIVE".equals(media.getStatus())) {
			// Keep the same error for absent, disabled and out-of-scope media.
			throw new IllegalArgumentException("资源不存在");
		}
	}

}
