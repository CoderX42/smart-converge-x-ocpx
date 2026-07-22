package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_tracking_task")
public class TrackingTask {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@NotBlank
	private String mediaId;

	@NotBlank
	private String code;

	@NotBlank
	private String name;

	@NotBlank
	private String linkId;

	@NotBlank
	private String channelId;

	private Boolean impressionEnabled;

	private Boolean rtaEnabled;

	private String rtaEndpointId;

	private String exposureReportUrl;

	private Boolean bigClickEnabled;

	@Min(0)
	private Integer dedupWindowSec;

	@Min(1)
	private Integer attributionWindowHours;

	@Min(1)
	private Integer dailyConversionLimit;

	private String callbackTargetRef;

	private Integer configVersion;

	private String status;

	private LocalDateTime archivedAt;

	@Version
	private Integer version;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
