package com.yzytech.scx.ocpx.feedback.web;

import com.alibaba.fastjson2.annotation.JSONField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 媒体 Feedback 的四实体路由参数与事件类型。 */
@Data
public class FeedbackRoutingRequest {

	/** 下游媒体主键，对应 {@code OcpxMedia.id}。 */
	@JSONField(name = "mId")
	@NotBlank
	@Size(max = 32)
	private String mId;

	/** 监测任务主键，对应 {@code OcpxMonitorTask.id}。 */
	@JSONField(name = "tId")
	@NotBlank
	@Size(max = 32)
	private String tId;

	/** 上游广告主主键，对应 {@code OcpxAdvertiser.id}；不是广告主侧广告 ID {@code advId}。 */
	@JSONField(name = "aId")
	@NotBlank
	@Size(max = 32)
	private String aId;

	/** 推广链接主键，对应 {@code OcpxPromotionLink.id}。 */
	@JSONField(name = "lId")
	@NotBlank
	@Size(max = 32)
	private String lId;

	/** 媒体事件代码。 */
	@NotBlank
	private String event;

}
