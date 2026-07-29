package com.yzytech.scx.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scx_placement_quality_snapshot")
public class PlacementQualitySnapshot {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String placementId;

	private LocalDate snapshotDate;

	private BigDecimal ctr30d;

	private BigDecimal cvr30d;

	private BigDecimal avgCpm;

	private BigDecimal avgCpc;

	private BigDecimal fillRate;

	private BigDecimal viewabilityRate;

	private LocalDateTime snapshotAt;

}
