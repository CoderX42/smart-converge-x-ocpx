package com.yzytech.scx.ocpx.contract;

/**
 * Adapter 对一次外部 HTTP 响应的业务判定。
 *
 * <p>
 * 只看 HTTP 2xx 并不足以判断所有合作方成功，例如部分平台会返回 HTTP 200 和失败业务码。本对象把“是否成功”和 “失败原因”放在一次判定中，避免
 * 同步转发服务重复解析响应体，也避免通用服务理解某个媒体的私有 code。
 * </p>
 *
 * @param success 当前合作方是否把本次请求视为成功
 * @param reason 失败时写入投递任务的稳定原因；成功时为 {@code null}
 * @param businessCode 合作方返回的可选业务码
 * @param businessMessage 合作方返回的可选脱敏业务说明
 */
public record AdapterDeliveryResult(boolean success, String reason, String businessCode, String businessMessage) {

	public static AdapterDeliveryResult success(String businessCode, String businessMessage) {
		return new AdapterDeliveryResult(true, null, businessCode, businessMessage);
	}

	public static AdapterDeliveryResult failure(String reason, String businessCode, String businessMessage) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("adapter failure reason is required");
		}
		return new AdapterDeliveryResult(false, reason, businessCode, businessMessage);
	}

}
