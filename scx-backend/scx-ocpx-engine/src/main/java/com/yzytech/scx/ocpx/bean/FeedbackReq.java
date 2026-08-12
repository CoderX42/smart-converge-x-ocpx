package com.yzytech.scx.ocpx.bean;

import com.yzytech.scx.ocpx.constant.enums.DeviceIdTypeEnum;
import com.yzytech.scx.ocpx.constant.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.constant.enums.OsTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackReq {

	private String chid;

	private String mid;

	private String dspid;

	private String linkid;

	private EventTypeEnum event;

	private String timeKeyRes;

	private int showSmt;

	private int uniShowSmt;

	private int clkSmt;

	private int uniClkSmt;

	private String callbackUrl;

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

	private String advId;

	private String groupId;

	private String planId;

	private String creativeId;

	private String impresssId;

	private String clickId;

	private String reqId;

	private String marke;

	private String model;

	private String ext;

	private boolean flagLog;

	private String userId;

	private DeviceIdTypeEnum userType;

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
