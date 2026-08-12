package com.yzytech.scx.ocpx.callback.service;

/**
 * Callback 处理结果。
 *
 * @param duplicate 是否已处理过同一请求 ID 和动作类型
 * @param decision 本次请求的最终处置
 */
public record CallbackHandleResult(boolean duplicate, CallbackDecision decision) {
}
