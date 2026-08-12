package com.yzytech.scx.ocpx.manager.union;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.constant.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.constant.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.index.ChannelMonitorIndex;
import com.yzytech.scx.ocpx.manager.union.dto.UnionCallbackReq;
import com.yzytech.scx.ocpx.util.AesUtil;
import com.yzytech.scx.ocpx.util.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public class UnionParamAdapter {

	@Value("${domain.name}")
	private String domainName;

	@Value("${app.callback-aes-key}")
	private String callbackAesKey;

	private final ChannelMonitorIndex channelMonitorIndex;

	public UnionParamAdapter(ChannelMonitorIndex channelMonitorIndex) {
		this.channelMonitorIndex = channelMonitorIndex;
	}

	public String getCallbackUrl(FeedbackReq feedback) {
		String state = feedback.getLinkid() + "_" + feedback.getMid();
		String callbackExt = AesUtil.encryptBase64(JSON.toJSONString(feedback), callbackAesKey);
		return domainName + "/union/callback?ext=" + callbackExt + "&s=" + state;
	}

	public CallbackReq getCallbackReq(UnionCallbackReq request) {
		String ext = Objects.requireNonNull(AesUtil.decryptBase64(request.getExt(), callbackAesKey));
		return CallbackReq.builder()
			.feedback(JSON.parseObject(ext, FeedbackReq.class))
			.actionType(ActionTypeEnum.getActionTypeEnum(request.getEvent_type()))
			.actionTime(request.getEvent_time())
			.build();
	}

	public String getReqUrl(OcpxAdvertiser advertiser, FeedbackReq feedback) {
		String bidUrl = advertiser.getBidUrl();
		OcpxMonitorTask monitorTask = channelMonitorIndex.get(feedback.getMid());
		String bizId = monitorTask == null ? "__BIZ_ID__" : monitorTask.getTaskCode();
		String requestUrl = bidUrl
			.replace("__REQUEST_ID__", StringUtil.replaceDefault(feedback.getReqId(), "__REQUEST_ID__"))
			.replace("__IMEI__", StringUtil.replaceDefault(feedback.getImei(), "__IMEI__"))
			.replace("__OAID__", StringUtil.replaceDefault(feedback.getOaid(), "__OAID__"))
			.replace("__OAID_MD5__", StringUtil.replaceDefault(feedback.getOaidMd5(), "__OAID_MD5__"))
			.replace("__IDFA__", StringUtil.replaceDefault(feedback.getIdfa(), "__IDFA__"))
			.replace("__IMEI_MD5__", StringUtil.replaceDefault(feedback.getImeiMd5(), "__IMEI_MD5__"))
			.replace("__IP__", StringUtil.replaceDefault(feedback.getIp(), "__IP__"))
			.replace("__UA__", StringUtil.replaceDefaultEncode(feedback.getUa(), "__UA__"))
			.replace("__MODEL__", StringUtil.replaceDefaultEncode(feedback.getModel(), "__MODEL__"))
			.replace("__OS__", StringUtil.replaceDefault(matchOs(feedback.getOs()), "__OS__"))
			.replace("__MAC__", StringUtil.replaceDefault(feedback.getMac(), "__MAC__"))
			.replace("__CAID__", StringUtil.replaceDefault(feedback.getCaid(), "__CAID__"))
			.replace("__MTS__", StringUtil.replaceDefault(feedback.getTsMs(), "__MTS__"))
			.replace("__CALLBACK_URL__", StringUtil.replaceDefaultEncode(getCallbackUrl(feedback), "__CALLBACK_URL__"))
			.replace("__BIZ_ID__", bizId);
		String md5 = DigestUtils
			.md5DigestAsHex((requestUrl + advertiser.getCredentialConfigKey()).getBytes(StandardCharsets.UTF_8));
		return requestUrl + "&signature=" + md5;
	}

	private String matchOs(OsTypeEnum os) {
		if (os == null) {
			return "3";
		}
		return switch (os) {
			case IOS -> "1";
			case ANDROID -> "0";
			default -> "3";
		};
	}

}
