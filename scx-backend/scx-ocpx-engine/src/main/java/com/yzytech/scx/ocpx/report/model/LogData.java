package com.yzytech.scx.ocpx.report.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * Redis 报表队列与 MyBatis 批量落库共享的数据模型。
 *
 * <p>
 * 模型同时容纳 Feedback 基础指标和 Callback 转化指标。字符串 getter 会按数据库列长度截断，
 * 防止异常合作方参数导致批量写入失败；此截断不修改对象原字段值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogData {

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

	/** 事件、Engine 接收时间和曝光/点击增量指标。 */
	private String event;

	private String timeKeyRes;

	private int showSmt;

	private int uniShowSmt;

	private int clkSmt;

	private int uniClkSmt;

	/** 原媒体回调地址与设备标识信息。 */
	private String callbackUrl;

	private String caid;

	private String caid1;

	private String lastCaid;

	private String prevCaid;

	private String idfa;

	private String imei;

	private String oaid;

	private String oaidMd5;

	private String androidid;

	private String mac;

	private String ip;

	private OsTypeEnum os;

	/** 请求时间、客户端环境及广告层级标识。 */
	private String ts;

	private String tsMs;

	private String ua;

	private String advId;

	private String groupId;

	private String planId;

	private String creativeId;

	private String impresssId;

	private String clickId;

	private String reqId;

	private String marke;

	private String model;

	/** 由设备标识归一化得到的用户标识及类型。 */
	private String userId;

	private String userType;

	/** 十一类标准 Callback 转化的本条增量指标。 */
	private int activate;

	private int evoke;

	private int login;

	private int register;

	private int payment;

	private int customerAcquisition;

	private int nextStay;

	private int visits;

	private int authentication;

	private int placeOrder;

	private int finishTheOrder;

	/** 转化发生时间和监测任务基础价格。 */
	private long actionTime;

	private BigInteger basePrice;

	public String getTimeKeyRes() {
		return StringUtil.checkStrLength(timeKeyRes, 50);
	}

	public String getCallbackUrl() {
		return StringUtil.checkStrLength(callbackUrl, 3000);
	}

	public String getCaid() {
		return StringUtil.checkStrLength(caid, 50);
	}

	public String getCaid1() {
		return StringUtil.checkStrLength(caid1, 50);
	}

	public String getLastCaid() {
		return StringUtil.checkStrLength(lastCaid, 50);
	}

	public String getPrevCaid() {
		return StringUtil.checkStrLength(prevCaid, 50);
	}

	public String getIdfa() {
		return StringUtil.checkStrLength(idfa, 50);
	}

	public String getImei() {
		return StringUtil.checkStrLength(imei, 50);
	}

	public String getOaid() {
		return StringUtil.checkStrLength(oaid, 100);
	}

	public String getOaidMd5() {
		return StringUtil.checkStrLength(oaidMd5, 40);
	}

	public String getAndroidid() {
		return StringUtil.checkStrLength(androidid, 50);
	}

	public String getMac() {
		return StringUtil.checkStrLength(mac, 50);
	}

	public String getIp() {
		return StringUtil.checkStrLength(ip, 32);
	}

	public String getTs() {
		return StringUtil.checkStrLength(ts, 50);
	}

	public String getTsMs() {
		return StringUtil.checkStrLength(tsMs, 50);
	}

	public String getUa() {
		return StringUtil.checkStrLength(ua, 2000);
	}

	public String getAdvId() {
		return StringUtil.checkStrLength(advId, 50);
	}

	public String getGroupId() {
		return StringUtil.checkStrLength(groupId, 50);
	}

	public String getPlanId() {
		return StringUtil.checkStrLength(planId, 50);
	}

	public String getCreativeId() {
		return StringUtil.checkStrLength(creativeId, 50);
	}

	public String getImpresssId() {
		return StringUtil.checkStrLength(impresssId, 50);
	}

	public String getClickId() {
		return StringUtil.checkStrLength(clickId, 50);
	}

	public String getReqId() {
		return StringUtil.checkStrLength(reqId, 50);
	}

	public String getMarke() {
		return StringUtil.checkStrLength(marke, 32);
	}

	public String getModel() {
		return StringUtil.checkStrLength(model, 32);
	}

	public String getUserId() {
		return StringUtil.checkStrLength(userId, 100);
	}

	public String getUserType() {
		return StringUtil.checkStrLength(userType, 15);
	}

}
