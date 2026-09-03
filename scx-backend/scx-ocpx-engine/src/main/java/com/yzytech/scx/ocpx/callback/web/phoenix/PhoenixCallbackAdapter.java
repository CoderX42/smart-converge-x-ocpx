package com.yzytech.scx.ocpx.callback.web.phoenix;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxMediaIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import com.yzytech.scx.ocpx.protocol.PhoenixTemplateContract;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.model.OcpxConversionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/** 凤凰新闻字符串事件与 AES-GCM Feedback 上下文适配器。 */
@Component
@RequiredArgsConstructor
public class PhoenixCallbackAdapter {

	private static final Map<String, ActionTypeEnum> ACTIONS = Map.ofEntries(Map.entry("active", ActionTypeEnum.ACTIVATE),
			Map.entry("recall", ActionTypeEnum.EVOKE), Map.entry("register", ActionTypeEnum.REGISTER),
			Map.entry("pay", ActionTypeEnum.PAYMENT), Map.entry("pay_amount", ActionTypeEnum.PAYMENT),
			Map.entry("next_day", ActionTypeEnum.NEXT_STAY), Map.entry("first_order", ActionTypeEnum.FIRST_ORDER),
			Map.entry("download", ActionTypeEnum.DOWNLOAD), Map.entry("activity", ActionTypeEnum.ACTIVITY),
			Map.entry("addiction", ActionTypeEnum.ADDICTION));

	private final OcpxKeyRingCrypto callbackCrypto;

	private final OcpxAdvertiserIndex advertiserIndex;

	private final OcpxPromotionLinkIndex promotionLinkIndex;

	private final OcpxMonitorTaskIndex monitorTaskIndex;

	private final OcpxMediaIndex mediaIndex;

	public CallbackReq toCallback(PhoenixCallbackRequest request) {
		if (request == null || !StringUtils.hasText(request.getKid()) || !StringUtils.hasText(request.getExt())
				|| request.getKid().length() > 32 || request.getExt().length() > 12000
				|| (request.getEvent_type() != null && request.getEvent_type().length() > 32)) {
			throw new IllegalArgumentException("missing PHOENIX callback parameter");
		}
		String originalEventType = request.getEvent_type();
		String partnerEvent = originalEventType == null ? "active" : originalEventType.trim().toLowerCase(Locale.ROOT);
		if (!StringUtils.hasText(partnerEvent)) {
			throw new IllegalArgumentException("blank PHOENIX event_type");
		}
		ActionTypeEnum actionType = ACTIONS.get(partnerEvent);
		if (actionType == null) {
			throw new IllegalArgumentException("unknown PHOENIX event_type");
		}
		String clearText = callbackCrypto.decryptCallback(request.getKid(), request.getExt());
		if (!StringUtils.hasText(clearText)) {
			throw new IllegalArgumentException("invalid PHOENIX callback ext");
		}
		try {
			FeedbackReq feedback = JSON.parseObject(clearText, FeedbackReq.class);
			requirePhoenixRoute(feedback);
			return CallbackReq.builder()
					.feedback(feedback)
					.actionType(actionType)
					.actionTime(System.currentTimeMillis())
					.partnerActionType(originalEventType == null ? "active" : originalEventType)
					.currencyAmount(null)
					.build();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("invalid PHOENIX callback ext", exception);
		}
	}

	private void requirePhoenixRoute(FeedbackReq feedback) {
		if (feedback == null || feedback.getEvent() != EventTypeEnum.CLICK) {
			throw new IllegalArgumentException("PHOENIX callback ext event is not CLICK");
		}
		requireRouteId(feedback.getMId(), "mId");
		requireRouteId(feedback.getTId(), "tId");
		requireRouteId(feedback.getAId(), "aId");
		requireRouteId(feedback.getLId(), "lId");
		if (!StringUtils.hasText(feedback.getReqId()) || feedback.getReqId().length() > 128) {
			throw new IllegalArgumentException("PHOENIX callback ext has invalid reqId");
		}
		OcpxAdvertiser advertiser = advertiserIndex.get(feedback.getAId());
		OcpxPromotionLink link = promotionLinkIndex.get(feedback.getLId());
		OcpxMonitorTask task = monitorTaskIndex.get(feedback.getTId());
		OcpxMedia media = mediaIndex.get(feedback.getMId());
		if (advertiser == null || advertiser.getUpstreamProtocol() != OcpxUpstreamProtocol.PHOENIX || link == null
				|| task == null || media == null || Boolean.TRUE.equals(link.getDeleted())
				|| Boolean.TRUE.equals(task.getDeleted()) || !feedback.getAId().equals(link.getAdvertiserId())
				|| !feedback.getAId().equals(task.getAdvertiserId()) || !feedback.getLId().equals(task.getPromotionLinkId())
				|| !feedback.getMId().equals(task.getMediaId())
				|| !OcpxConversionEvent.supports(task.getConversionEvent())) {
			throw new IllegalArgumentException("PHOENIX callback route mismatch");
		}
		PhoenixTemplateContract.requireValid(link.getLinkUrl(), link.getPlatform());
	}

	private void requireRouteId(String value, String name) {
		if (!StringUtils.hasText(value) || value.length() > 32) {
			throw new IllegalArgumentException("PHOENIX callback ext has invalid SCX route id: " + name);
		}
	}

}
