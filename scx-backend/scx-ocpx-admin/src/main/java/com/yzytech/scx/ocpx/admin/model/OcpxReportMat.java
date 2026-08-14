package com.yzytech.scx.ocpx.admin.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("scx_ocpx_report_mat")
@EqualsAndHashCode(callSuper = true)
public class OcpxReportMat extends Model<OcpxReportMat> {

	private Integer timeHour;

	/** 下游媒体主键，对应 {@code OcpxMedia.id}。 */
	private String mId;

	/** 上游广告主主键，对应 {@code OcpxAdvertiser.id}；不是广告主侧广告 ID {@code advId}。 */
	private String aId;

	private Integer appId;

	private Integer slotId;

	private Integer dspSlotId;

	/** 监测任务主键，对应 {@code OcpxMonitorTask.id}。 */
	private String tId;

	/** 推广链接主键，对应 {@code OcpxPromotionLink.id}。 */
	private String lId;

	private Integer reqSmt;

	private Integer resSmt;

	private Integer uniWinSmt;

	private Integer showSmt;

	private Integer uniShowSmt;

	private Integer clkSmt;

	private Integer uniClkSmt;

	private Integer activate;

	private Integer evoke;

	private Integer login;

	private Integer register;

	private Integer payment;

	private Integer customerAcquisition;

	private Integer nextStay;

	private Integer visits;

	private Integer authentication;

	private Integer placeOrder;

	private Integer finishTheOrder;

	private Integer firstActivation;

	private Integer firstOrder;

	private BigDecimal currencyAmount;

	private Long basePrice;

	private Long winPrice;

	private Integer mark;

}
