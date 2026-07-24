package com.yzytech.scx.subject.service;

import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Media access helper.
 * <p>
 * Object-level media isolation ({@code scx_user_subject_scope}) is disabled: any
 * authenticated caller with the corresponding menu/API permission may access
 * all media. Menu/button RBAC via Sa-Token remains enforced at controllers.
 */
@Service
public class MediaScopeService {

	public boolean isAdmin() {
		BootUser user = SecurityUtils.getUser();
		return user != null && user.getRoles() != null && user.getRoles().contains(CommonConstants.ROLE_ADMIN_CODE);
	}

	/**
	 * Empty list means "no per-user media restriction" (all ACTIVE media are
	 * visible). Kept for call-site compatibility.
	 */
	public List<String> readableMediaIds() {
		return Collections.emptyList();
	}

	public boolean canRead(String mediaId) {
		return StringUtils.hasText(mediaId);
	}

	public boolean canWrite(String mediaId) {
		return StringUtils.hasText(mediaId);
	}

}
