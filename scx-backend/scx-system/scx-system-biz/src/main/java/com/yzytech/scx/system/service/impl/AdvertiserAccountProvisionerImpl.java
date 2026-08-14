package com.yzytech.scx.system.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import com.yzytech.scx.system.api.entity.SysRole;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.entity.SysUserRole;
import com.yzytech.scx.system.api.service.AdvertiserAccountProvisioner;
import com.yzytech.scx.system.api.util.SystemPasswordPolicy;
import com.yzytech.scx.system.mapper.SysRoleMapper;
import com.yzytech.scx.system.mapper.SysUserMapper;
import com.yzytech.scx.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 广告主登录账号开户实现。
 */
@Service
@RequiredArgsConstructor
public class AdvertiserAccountProvisionerImpl implements AdvertiserAccountProvisioner {

	private final SysUserMapper userMapper;

	private final SysRoleMapper roleMapper;

	private final SysUserRoleMapper userRoleMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ProvisionedAccount provision(String advertiserId, String advertiserName, String username,
			String rawPassword) {
		String normalizedAdvertiserId = required(advertiserId, "广告主编号不能为空", 64);
		String normalizedAdvertiserName = required(advertiserName, "广告主名称不能为空", 100);
		String normalizedUsername = required(username, "用户名不能为空", 100);
		SystemPasswordPolicy.validate(rawPassword);
		if (userMapper.countByUsernameIncludingDeleted(normalizedUsername) > 0) {
			throw new BootBusinessException("用户名已存在");
		}

		SysRole advertiserRole = roleMapper.selectOne(
				Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, SystemAccountConstants.ROLE_ADVERTISER_CODE));
		if (advertiserRole == null || !SystemAccountConstants.ROLE_ADVERTISER_ID.equals(advertiserRole.getId())
				|| !"0".equals(advertiserRole.getType()) || advertiserRole.getShopId() != null) {
			throw new IllegalStateException("固定广告主角色不存在或配置无效");
		}

		SysUser user = new SysUser();
		user.setUsername(normalizedUsername);
		user.setPassword(BCrypt.hashpw(rawPassword));
		user.setNickname(normalizedAdvertiserName);
		user.setStatus("0");
		user.setType(SystemAccountConstants.USER_TYPE_ADVERTISER);
		user.setShopId(normalizedAdvertiserId);
		user.setDelFlag("0");
		if (userMapper.insert(user) != 1) {
			throw new IllegalStateException("广告主登录账号创建失败");
		}

		SysUserRole relation = new SysUserRole();
		relation.setUserId(user.getId());
		relation.setRoleId(advertiserRole.getId());
		if (userRoleMapper.insert(relation) != 1) {
			throw new IllegalStateException("广告主登录账号角色绑定失败");
		}
		return new ProvisionedAccount(user.getId(), normalizedUsername);
	}

	private String required(String value, String message, int maxLength) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(message);
		}
		String normalized = value.trim();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException("字段长度不能超过" + maxLength + "位");
		}
		return normalized;
	}

}
