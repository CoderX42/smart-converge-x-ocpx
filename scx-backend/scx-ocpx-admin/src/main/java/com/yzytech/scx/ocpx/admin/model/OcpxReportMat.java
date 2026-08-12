package com.yzytech.scx.ocpx.admin.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("scx_ocpx_report_mat")
@EqualsAndHashCode(callSuper = true)
public class OcpxReportMat extends Model<OcpxReportMat> {

	private Integer timeHour;

	private String channelId;

	private String dspId;

	private Integer appId;

	private Integer slotId;

	private Integer dspSlotId;

	private String monitorId;

	private String linkId;

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

	private Long basePrice;

	private Long winPrice;

	private Integer mark;

}
