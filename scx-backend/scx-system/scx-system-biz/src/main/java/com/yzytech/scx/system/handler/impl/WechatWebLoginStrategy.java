

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
 * 微信网页登录策略实现
 *
 */

@Component
@RequiredArgsConstructor
public class WechatWebLoginStrategy implements SocialLoginStrategy {

	private final SysUserMapper sysUserMapper;

	@Override
	public String getOpenId(String code, SysSocialAccount account) {
		String url = String.format(SecurityConstants.WX_AUTH_URL, account.getAppId(), account.getAppSecret(), code);
		String result = HttpUtil.get(url);
		return JSONUtil.parseObj(result).getStr("openid");
	}

	@Override
	public String getType() {
		return "1";
	}

	@Override
	public SysUser selectUserByOpenId(String openId) {
		return sysUserMapper.selectUserByOpenId(openId);
	}

	@Override
	public void bindUser(SysUser user, String openId) {
		// 公众号绑定 openid
		if (StringUtils.hasText(user.getWxOpenId()) && !user.getWxOpenId().equals(openId)) {
			throw new BootBusinessException("该公众号账号已被绑定，请解绑后重新绑定");
		}
		user.setWxOpenId(openId);
	}

	@Override
	public boolean unbindUser(SysUser user) {
		LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, user.getId())
			.set(SysUser::getWxOpenId, null);
		return sysUserMapper.update(null, wrapper) > 0;
	}

}
