package com.yzytech.scx.ocpx.feedback.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.yzytech.scx.ocpx.shared.enums.DeviceIdTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * Engine 内部统一的媒体 Feedback 模型。
 *
 * <p>
 * 该对象由不同媒体协议的入站 DTO 适配而来，随后同时用于去重、报表记录、广告主 URL 宏替换， 并加密写入 Callback 的 {@code ext} 参数。四个业务
 * ID 均为 SCX 字符串主键。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackReq {

	/** 下游媒体主键，对应 {@code OcpxMedia.id}。 */
	@JSONField(name = "mId")
	private String mId;

	/** 监测任务主键，对应 {@code OcpxMonitorTask.id}。 */
	@JSONField(name = "tId")
	private String tId;

	/** 上游广告主主键，对应 {@code OcpxAdvertiser.id}；不是广告主侧广告 ID {@code advId}。 */
	@JSONField(name = "aId")
	private String aId;

	/** 推广链接主键，对应 {@code OcpxPromotionLink.id}。 */
	@JSONField(name = "lId")
	private String lId;

	/** 标准化事件及 Engine 接收时间。 */
	private EventTypeEnum event;

	private String timeKeyRes;

	/** 曝光、去重曝光、点击和去重点击的本条增量指标。 */
	private int showSmt;

	private int uniShowSmt;

	private int clkSmt;

	private int uniClkSmt;

	/** 下游媒体提供的回调地址；入站适配后以严格 URL 编码形式保存。 */
	private String callbackUrl;

	/** 设备标识与设备环境信息。 */
	private String caid;

	private String caidVersion;

	private String caid1;

	private String lastCaid;

	private String prevCaid;

	private String idfa;

	private String imei;

	private String imeiMd5;

	private String oaid;

	private String oaidMd5;

	private String androidid;

	private String mac;

	private String ip;

	private OsTypeEnum os;

	private String ts;

	private String tsMs;

	private String ua;

	/** 广告主侧的广告、组、计划、创意、曝光和点击标识。 */
	private String advId;

	private String groupId;

	private String planId;

	private String creativeId;

	private String impresssId;

	private String clickId;

	/** 本次请求唯一标识；缺失时由完整入站参数计算 MD5。 */
	private String reqId;

	private String marke;

	private String model;

	/** 合作方扩展数据及抽样日志标记。 */
	private String ext;

	private boolean flagLog;

	/** 按优先级从设备标识中推导的统一用户标识及其类型。 */
	private String userId;

	private DeviceIdTypeEnum userType;

	/**
	 * 按 OAID、CAID、IDFA、IMEI 的优先级选择统一用户标识，并同步更新 {@code userType}。
	 * @return 首个非空设备标识；均为空时返回空字符串
	 */
	public String getUserId() {
		if (StringUtils.hasText(oaid)) {
			userType = DeviceIdTypeEnum.OAID;
			return oaid;
		}
		if (StringUtils.hasText(caid)) {
			userType = DeviceIdTypeEnum.CAID;
			return caid;
		}
		if (StringUtils.hasText(idfa)) {
			userType = DeviceIdTypeEnum.IDFA;
			return idfa;
		}
		if (StringUtils.hasText(imei)) {
			userType = DeviceIdTypeEnum.IMEI;
			return imei;
		}
		return "";
	}

}
