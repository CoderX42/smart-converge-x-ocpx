package com.yzytech.scx.ocpx.bean;

import com.yzytech.scx.ocpx.constant.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogData {

	private String chid;

	private String mid;

	private String dspid;

	private String linkid;

	private String event;

	private String timeKeyRes;

	private int showSmt;

	private int uniShowSmt;

	private int clkSmt;

	private int uniClkSmt;

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

	private String userId;

	private String userType;

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
