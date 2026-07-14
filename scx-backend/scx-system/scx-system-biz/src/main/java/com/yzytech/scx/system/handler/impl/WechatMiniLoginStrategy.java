

package com.yzytech.scx.system.handler.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yzytech.scx.common.core.constant.SecurityConstants;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.system.api.entity.SysSocialAccount;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.handler.SocialLoginStrategy;
import com.yzytech.scx.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 微信小程序登录策略实现
 *
 */

@Component
@RequiredArgsConstructor
public class WechatMiniLoginStrategy implements SocialLoginStrategy {

	private final SysUserMapper sysUserMapper;

	@Override
	public String getOpenId(String code, SysSocialAccount account) {
		String url = String.format(SecurityConstants.WX_MINI_AUTH_URL, account.getAppId(), account.getAppSecret(),
				code);
		String result = HttpUtil.get(url);
		return JSONUtil.parseObj(result).getStr("openid");
	}

	@Override
	public String getType() {
		return "2";
	}

	@Override
	public SysUser selectUserByOpenId(String openId) {
		return sysUserMapper.selectUserByMiniOpenId(openId);
	}

	@Override
	public void bindUser(SysUser user, String openId) {
		// 小程序绑定 miniOpenId
		if (StringUtils.hasText(user.getWxMiniOpenId()) && !user.getWxMiniOpenId().equals(openId)) {
			throw new BootBusinessException("该小程序账号已被绑定，请解绑后重新绑定");
		}
		user.setWxMiniOpenId(openId);
	}

	@Override
	public boolean unbindUser(SysUser user) {
		LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, user.getId())
			.set(SysUser::getWxMiniOpenId, null);
		return sysUserMapper.update(null, wrapper) > 0;
	}

}
