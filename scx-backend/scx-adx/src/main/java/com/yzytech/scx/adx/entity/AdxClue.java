package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_adx_clue")
public class AdxClue {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private Integer sourceType;

	private Integer clueStatus;

	private String clueName;

	private String cluePhone;

	private String clueIndex;

	private String sonTaskId;

	private String clueCityId;

	private String clueAreaId;

	private String clueBrandId;

	private String clueCarSeriesId;

	private String clueCarModelId;

	private LocalDateTime firstCarRegistrationTime;

	private BigDecimal carMileage;

	private String remark;

	private String submitMessage;

	private LocalDateTime submittedAt;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
