package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Media access helper.
 * <p>
 * Object-level media isolation is enforced through {@code scx_user_subject_scope}:
 * administrators read and write every media; other users only reach media explicitly
 * granted to them. Callers must return the same uniform not-found response for "no scope"
 * and "does not exist" so unauthorized objects cannot be inferred.
 */
@Service
@RequiredArgsConstructor
public class MediaScopeService {

	private final UserSubjectScopeMapper scopeMapper;

	public boolean isAdmin() {
		BootUser user = SecurityUtils.getUser();
		return user != null && user.getRoles() != null && user.getRoles().contains(CommonConstants.ROLE_ADMIN_CODE);
	}

	/**
	 * Media ids readable by the current user. Administrators get an empty list, which
	 * call sites treat as "no per-user media restriction".
	 */
	public List<String> readableMediaIds() {
		if (isAdmin())
			return Collections.emptyList();
		BootUser user = SecurityUtils.getUser();
		if (user == null)
			return List.of();
		return scopeMapper
			.selectList(Wrappers.<UserSubjectScope>lambdaQuery()
				.eq(UserSubjectScope::getUserId, user.getUserId())
				.eq(UserSubjectScope::getSubjectType, "MEDIA"))
			.stream()
			.map(UserSubjectScope::getSubjectId)
			.toList();
	}

	public boolean canRead(String mediaId) {
		return isAdmin() || readableMediaIds().contains(mediaId);
	}

	public boolean canWrite(String mediaId) {
		BootUser user = SecurityUtils.getUser();
		return user != null && (isAdmin()
				|| (user.getRoles() != null && user.getRoles().contains("ROLE_OPERATOR") && canRead(mediaId)));
	}

	public String currentUserId() {
		BootUser user = SecurityUtils.getUser();
		return user == null ? null : user.getUserId();
	}

}
