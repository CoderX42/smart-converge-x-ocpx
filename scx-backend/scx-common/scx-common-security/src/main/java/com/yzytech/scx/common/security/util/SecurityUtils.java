

package com.yzytech.scx.common.security.util;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.common.core.constant.CacheConstants;
import com.yzytech.scx.common.core.enums.DeviceTypeEnum;
import com.yzytech.scx.common.security.entity.BootUser;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * 安全工具类
 *
 * @since 2022/11/25 21:25
 */
@Slf4j
@UtilityClass
public class SecurityUtils {

	public static void login(BootUser hxUser) {
		SaHolder.getStorage().set(CacheConstants.USER_CACHE, hxUser);
		StpUtil.login(hxUser.getUserId());
		StpUtil.getTokenSession().set(CacheConstants.USER_CACHE, hxUser);
	}

	public static void loginByDevice(BootUser hxUser, DeviceTypeEnum deviceTypeEnum) {
		SaHolder.getStorage().set(CacheConstants.USER_CACHE, hxUser);
		StpUtil.login(hxUser.getUserId(), deviceTypeEnum.getDevice());
		StpUtil.getTokenSession().set(CacheConstants.USER_CACHE, hxUser);
	}

	private boolean isWebRequest() {
		ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder
			.getRequestAttributes();
		return Objects.isNull(servletRequestAttributes);
	}

	/**
	 * 获取用户信息
	 * @return
	 */
	public BootUser getUser() {
		if (isWebRequest()) {
			return null;
		}
		// 未登录直接返回
		if (!StpUtil.isLogin()) {
			return null;
		}
		SaSession saSession = StpUtil.getTokenSession();
		return (BootUser) saSession.get(CacheConstants.USER_CACHE);
	}

	/**
	 * 获取用户信息
	 * @return
	 */
	public BootUser getUser(String token) {
		return (BootUser) StpUtil.getTokenSessionByToken(token).get(CacheConstants.USER_CACHE);
	}

	public String getUserName() {
		BootUser hxUser = getUser();
		if (Objects.isNull(hxUser)) {
			return null;
		}
		return hxUser.getUsername();
	}

	public String getUserId() {
		return getUser().getUserId();
	}

	public String getOpenId() {
		return getUser().getOpenId();
	}

}
