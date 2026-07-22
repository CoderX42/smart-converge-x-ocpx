package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_link_event_mapping")
public class LinkEventMapping {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String linkId;

	private Integer configVersion;

	@NotBlank
	private String internalEvent;

	@NotBlank
	private String externalEvent;

	private BigDecimal unitPrice;

	private Integer dailyLimit;

	private LocalDateTime createTime;

}
