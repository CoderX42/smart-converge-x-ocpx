package com.yzytech.scx.system.api.constant;

/**
 * 系统账号与固定角色常量。
 */
public final class SystemAccountConstants {

	/** 固定广告主共享角色主键。 */
	public static final String ROLE_ADVERTISER_ID = "role_advertiser";

	/** 固定广告主共享角色编码。 */
	public static final String ROLE_ADVERTISER_CODE = "ROLE_ADVERTISER";

	/** 广告主登录账号类型。 */
	public static final String USER_TYPE_ADVERTISER = "2";

	/** 管理共享广告主角色权限，仅授予受信任的系统管理角色。 */
	public static final String ADVERTISER_ROLE_MANAGE_PERMISSION = "ocpx:advertiser:role:manage";

	/** 广告主账号访问全部 OCPX 数据的显式范围权限。 */
	public static final String ADVERTISER_ALL_SCOPE_PERMISSION = "ocpx:advertiser:scope:all";

	/** 资金充值只能由真实系统管理员执行，不能下放到共享广告主角色。 */
	public static final String ADVERTISER_RECHARGE_PERMISSION = "ocpx:advertiser:recharge";

	/** 资金人工扣减只能由真实系统管理员执行，不能下放到共享广告主角色。 */
	public static final String ADVERTISER_DEDUCT_PERMISSION = "ocpx:advertiser:deduct";

	/** 监测任务计费口径仅允许拥有专用权限的内部账号维护。 */
	public static final String MONITOR_TASK_BILLING_PERMISSION = "ocpx:monitor-task:billing";

	/** 真实监测点击测试仅允许拥有专用权限的系统管理员执行。 */
	public static final String MONITOR_TASK_TEST_PERMISSION = "ocpx:monitor-task:test";

	private SystemAccountConstants() {
	}

}
