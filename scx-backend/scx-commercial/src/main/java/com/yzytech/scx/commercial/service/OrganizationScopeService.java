package com.yzytech.scx.commercial.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.commercial.dto.OrganizationScopeGrantRequest;
import com.yzytech.scx.commercial.entity.Organization;
import com.yzytech.scx.commercial.mapper.OrganizationMapper;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrganizationScopeService {

	public static final String ORGANIZATION = "ORGANIZATION";

	private final UserSubjectScopeMapper scopeMapper;

	private final OrganizationMapper organizationMapper;

	public boolean isAdmin() {
		BootUser user = SecurityUtils.getUser();
		return user != null && user.getRoles() != null && user.getRoles().contains(CommonConstants.ROLE_ADMIN_CODE);
	}

	public List<String> readableOrganizationIds() {
		if (isAdmin()) {
			return Collections.emptyList();
		}
		BootUser user = SecurityUtils.getUser();
		if (user == null) {
			return List.of();
		}
		return scopeMapper
			.selectList(Wrappers.<UserSubjectScope>lambdaQuery()
				.eq(UserSubjectScope::getUserId, user.getUserId())
				.eq(UserSubjectScope::getSubjectType, ORGANIZATION))
			.stream()
			.map(UserSubjectScope::getSubjectId)
			.toList();
	}

	public boolean canRead(String organizationId) {
		return StringUtils.hasText(organizationId) && (isAdmin() || readableOrganizationIds().contains(organizationId));
	}

	public boolean canWrite(String organizationId) {
		BootUser user = SecurityUtils.getUser();
		return user != null && StringUtils.hasText(organizationId) && (isAdmin() || (user.getRoles() != null
				&& user.getRoles().contains("ROLE_OPERATOR") && readableOrganizationIds().contains(organizationId)));
	}

	public void requireRead(String organizationId) {
		if (!canRead(organizationId)) {
			throw new IllegalArgumentException("资源不存在");
		}
	}

	public void requireWrite(String organizationId) {
		if (!canWrite(organizationId)) {
			throw new IllegalArgumentException("资源不存在");
		}
	}

	public Page<UserSubjectScope> page(Page<UserSubjectScope> page, String organizationId, String userId) {
		if (StringUtils.hasText(organizationId)) {
			requireActiveOrganization(organizationId);
		}
		return scopeMapper.selectPage(page,
				Wrappers.<UserSubjectScope>lambdaQuery()
					.eq(UserSubjectScope::getSubjectType, ORGANIZATION)
					.eq(StringUtils.hasText(organizationId), UserSubjectScope::getSubjectId, organizationId)
					.eq(StringUtils.hasText(userId), UserSubjectScope::getUserId, userId)
					.orderByDesc(UserSubjectScope::getCreateTime));
	}

	@Transactional
	public UserSubjectScope grant(OrganizationScopeGrantRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		requireActiveOrganization(request.organizationId());
		if (scopeMapper.countActiveUser(request.userId()) != 1) {
			throw new IllegalArgumentException("用户不存在或已停用");
		}
		UserSubjectScope existing = find(request.userId(), request.organizationId());
		if (existing != null) {
			return existing;
		}
		UserSubjectScope scope = new UserSubjectScope();
		scope.setUserId(request.userId());
		scope.setSubjectType(ORGANIZATION);
		scope.setSubjectId(request.organizationId());
		try {
			scopeMapper.insert(scope);
			return scope;
		}
		catch (DuplicateKeyException duplicate) {
			UserSubjectScope raced = find(request.userId(), request.organizationId());
			if (raced != null) {
				return raced;
			}
			throw duplicate;
		}
	}

	@Transactional
	public boolean revoke(String id, String organizationId) {
		UserSubjectScope scope = scopeMapper.selectById(id);
		if (scope == null || !ORGANIZATION.equals(scope.getSubjectType())
				|| !organizationId.equals(scope.getSubjectId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		return scopeMapper.deleteById(id) == 1;
	}

	private UserSubjectScope find(String userId, String organizationId) {
		return scopeMapper.selectOne(Wrappers.<UserSubjectScope>lambdaQuery()
			.eq(UserSubjectScope::getUserId, userId)
			.eq(UserSubjectScope::getSubjectType, ORGANIZATION)
			.eq(UserSubjectScope::getSubjectId, organizationId));
	}

	private void requireActiveOrganization(String organizationId) {
		Organization organization = organizationMapper.selectById(organizationId);
		if (organization == null || !"ACTIVE".equals(organization.getStatus())) {
			throw new IllegalArgumentException("资源不存在");
		}
	}

}
