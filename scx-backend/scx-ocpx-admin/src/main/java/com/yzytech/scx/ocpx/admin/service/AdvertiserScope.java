package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 将广告主账号的所有数据访问收敛到其绑定的广告主。 */
@Component
public class AdvertiserScope {

	/** 管理员保留请求条件；广告主账号无条件强制为会话绑定广告主。 */
	public String constrain(String requestedAdvertiserId) {
		String scoped = currentAdvertiserId();
		return scoped == null ? trim(requestedAdvertiserId) : scoped;
	}

	/** 写路径必须验证目标实体归属，不能依赖前端隐藏按钮。 */
	public void requireOwner(String advertiserId) {
		String scoped = currentAdvertiserId();
		if (scoped != null && !scoped.equals(trim(advertiserId))) {
			throw forbidden("无权访问其他广告主数据");
		}
	}

	/** 无法归属到单个广告主的管理动作仅允许管理账号执行。 */
	public void requireAdministrator() {
		if (currentAdvertiserId() != null) {
			throw forbidden("广告主账号无权执行该操作");
		}
	}

	/** 返回当前受限广告主；管理员返回 null。 */
	public String currentAdvertiserId() {
		BootUser user = SecurityUtils.getUser();
		if (user == null || !SystemAccountConstants.USER_TYPE_ADVERTISER.equals(user.getType())) {
			return null;
		}
		if (!StringUtils.hasText(user.getShopId())) {
			throw forbidden("广告主账号未绑定广告主");
		}
		return user.getShopId().trim();
	}

	private BootBusinessException forbidden(String message) {
		return new BootBusinessException(HttpStatus.FORBIDDEN.value(), message);
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
