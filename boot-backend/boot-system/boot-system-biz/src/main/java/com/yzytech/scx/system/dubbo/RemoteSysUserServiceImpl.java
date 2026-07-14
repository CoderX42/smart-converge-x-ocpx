

package com.yzytech.scx.system.dubbo;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.system.api.dto.SysUserReqDTO;
import com.yzytech.scx.system.api.entity.SysSocialAccount;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.remote.RemoteSysUserService;
import com.yzytech.scx.system.handler.SocialLoginFactory;
import com.yzytech.scx.system.handler.SocialLoginStrategy;
import com.yzytech.scx.system.mapper.SysSocialAccountMapper;
import com.yzytech.scx.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * @description
 * @date 2024/11/22
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSysUserServiceImpl implements RemoteSysUserService {

	private final ISysUserService sysUserService;

	private final SysSocialAccountMapper sysSocialAccountMapper;

	private final SocialLoginFactory socialLoginFactory;

	@Override
	public SysUser getUserInfo(String username) {
		// 首先通过用户名获取用户信息
		SysUser sysUser = sysUserService.findUserByName(username);
		if (ObjectUtil.isNull(sysUser)) {
			return null;
		}
		return sysUserService.findUserInfo(sysUser);
	}

	@Override
	public SysUser getUserInfoByPhone(String phone) {
		// 首先通过手机号获取用户信息
		SysUser sysUser = sysUserService.findUserByPhone(phone);
		if (ObjectUtil.isNull(sysUser)) {
			return null;
		}
		return sysUserService.findUserInfo(sysUser);
	}

	@Override
	public SysUser getUserInfoBySocialCode(String code, String type) {
		// 查询三方配置
		SysSocialAccount sysSocialAccount = sysSocialAccountMapper
			.selectOne(Wrappers.<SysSocialAccount>lambdaQuery().eq(SysSocialAccount::getType, type));

		SocialLoginStrategy strategy = socialLoginFactory.getStrategy(sysSocialAccount.getType());
		if (strategy == null) {
			throw new IllegalArgumentException("不支持的社交登录类型: " + type);
		}
		// 获取 openId
		String openId = strategy.getOpenId(code, sysSocialAccount);
		// 首先通过用户名获取用户信息
		SysUser sysUser = strategy.selectUserByOpenId(openId);
		if (ObjectUtil.isNull(sysUser)) {
			return null;
		}
		return sysUserService.findUserInfo(sysUser);
	}

	@Override
	public boolean createUser(SysUserReqDTO sysUserReqDTO) {
		return sysUserService.createUser(sysUserReqDTO);
	}

}
