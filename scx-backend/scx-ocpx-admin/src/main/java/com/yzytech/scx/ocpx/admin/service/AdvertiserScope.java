package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.core.constant.CommonConstants;
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

	/**
	 * 创意、素材和账务写路径使用严格主体范围；广告主的全量查询权限不能放大写权限。
	 */
	public String constrainSelfOwned(String requestedAdvertiserId) {
		BootUser user = SecurityUtils.getUser();
		if (user == null || !SystemAccountConstants.USER_TYPE_ADVERTISER.equals(user.getType())) {
			return trim(requestedAdvertiserId);
		}
		String owned = requireAdvertiserShopId(user);
		String requested = trim(requestedAdvertiserId);
		if (requested != null && !owned.equals(requested)) {
			throw forbidden("无权访问其他广告主数据");
		}
		return owned;
	}

	public void requireSelfOwnedAdvertiser(String advertiserId) {
		constrainSelfOwned(advertiserId);
	}

	/** 无法归属到单个广告主的管理动作仅允许管理账号执行。 */
	public void requireAdministrator() {
		if (currentAdvertiserId() != null) {
			throw forbidden("广告主账号无权执行该操作");
		}
	}

	/** 资金与账号管理只允许真实 ROLE_ADMIN；全量广告主数据范围不能提升为资金权限。 */
	public void requireSystemAdministrator() {
		BootUser user = SecurityUtils.getUser();
		if (user == null || SystemAccountConstants.USER_TYPE_ADVERTISER.equals(user.getType()) || user.getRoles() == null
				|| !user.getRoles().contains(CommonConstants.ROLE_ADMIN_CODE)) {
			throw forbidden("仅系统管理员可以维护资金与账号");
		}
	}

	/** 需要条件判断的资金写路径同时校验真实管理员角色与指定权限码。 */
	public void requireSystemAdministratorPermission(String permission) {
		requireSystemAdministrator();
		BootUser user = SecurityUtils.getUser();
		if (user.getPermissions() == null || !user.getPermissions().contains(permission)) {
			throw forbidden("系统管理员缺少资金操作权限");
		}
	}

	/** 计费配置允许授予内部运营角色，但广告主账号永远不能获得该写能力。 */
	public void requireInternalPermission(String permission) {
		BootUser user = SecurityUtils.getUser();
		if (user == null || SystemAccountConstants.USER_TYPE_ADVERTISER.equals(user.getType())
				|| user.getPermissions() == null || !user.getPermissions().contains(permission)) {
			throw forbidden("内部账号缺少计费配置权限");
		}
	}

	/**
	 * 账户金额使用比业务数据更严格的范围：广告主账号始终只能读取自身账户，即使拥有全量业务范围。
	 */
	public String accountAdvertiserId(String requestedAdvertiserId) {
		BootUser user = SecurityUtils.getUser();
		if (user == null || !SystemAccountConstants.USER_TYPE_ADVERTISER.equals(user.getType())) {
			return trim(requestedAdvertiserId);
		}
		if (!StringUtils.hasText(user.getShopId())) {
			throw forbidden("广告主账号未绑定广告主");
		}
		return user.getShopId().trim();
	}

	/** 返回当前受限广告主；管理员返回 null。 */
	public String currentAdvertiserId() {
		BootUser user = SecurityUtils.getUser();
		if (user == null || !SystemAccountConstants.USER_TYPE_ADVERTISER.equals(user.getType())) {
			return null;
		}
		if (user.getPermissions() != null
				&& user.getPermissions().contains(SystemAccountConstants.ADVERTISER_ALL_SCOPE_PERMISSION)) {
			return null;
		}
		if (!StringUtils.hasText(user.getShopId())) {
			throw forbidden("广告主账号未绑定广告主");
		}
		return user.getShopId().trim();
	}

	private String requireAdvertiserShopId(BootUser user) {
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
