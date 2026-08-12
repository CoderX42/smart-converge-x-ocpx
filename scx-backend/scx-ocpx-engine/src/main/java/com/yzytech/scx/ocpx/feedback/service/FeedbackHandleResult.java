package com.yzytech.scx.ocpx.feedback.service;

/**
 * Feedback 处理结果。
 *
 * @param firstReport 是否第一次收到同一请求 ID 和事件类型
 */
public record FeedbackHandleResult(boolean firstReport) {
}
