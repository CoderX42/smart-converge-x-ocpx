package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_monitor_link")
public class OcpxMonitorLink {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String taskId;

	private String taskMappingStatus;

	private String taskRouteKind;

	private Long taskConfigRevision;

	private String channelId;

	private String mediaAccountId;

	private String mediaCredentialId;

	private String advertiserId;

	private String appLinkId;

	private String monitorName;

	private String bizId;

	private BigDecimal unitPrice;

	private String remark;

	private String showUrl;

	private String clickUrl;

	private String upstreamFeedbackUrl;

	private String publishedUpstreamFeedbackUrl;

	private String downstreamCallbackUrl;

	private String routeStatus;

	private Boolean republishRequired;

	private String republishReason;

	private Long configVersion;

	private String rtaMode;

	private String billingMode;

	private Integer clickDedupWindowSeconds;

	private Integer eventSnapshotVersion;

	private String eventMappingMode;

	private LocalDateTime publishedAt;

	private Long publishedAdvertiserCredentialRevision;

	private Long publishedUpstreamAdapterVersion;

	private String publishedDocumentId;

	private Long publishedDocumentRevision;

	private String publishedProductId;

	private Long publishedProductVersion;

	private Long publishedProductEventPolicyRevision;

	private String publishedAppLinkId;

	private Long publishedAppLinkVersion;

	private String publishedAppLinkType;

	@JsonIgnore
	private String publishedLinkUrlTemplate;

	private String publishedLinkTemplateHost;

	private String publishedLinkTemplateFingerprint;

	private String publishedLinkTemplateMacroSet;

	private Boolean publishedRedirectEnabled;

	@JsonIgnore
	private String publishedDeeplinkUrl;

	@JsonIgnore
	private String publishedDownloadUrl;

	private String publishedMediaAccountId;

	private Long publishedMediaAccountVersion;

	private Long publishedMediaAccountCredentialRevision;

	private String publishedMediaCredentialId;

	private Long publishedMediaCredentialSecretRevision;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
