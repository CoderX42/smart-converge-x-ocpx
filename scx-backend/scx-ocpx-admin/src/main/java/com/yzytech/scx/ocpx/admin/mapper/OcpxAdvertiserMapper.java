package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.admin.dto.ManagementRows;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface OcpxAdvertiserMapper extends BaseMapper<OcpxAdvertiser> {

	List<ManagementRows.AdvertiserUsername> selectUsernamesByAdvertiserIds(@Param("ids") Collection<String> ids);

	OcpxAdvertiser selectByIdForUpdate(@Param("id") String id);

	List<ManagementRows.RouteKey> selectCascadeRoutes(@Param("id") String id);

	int deleteReportMatTemp(@Param("id") String id);

	int deleteReportMat(@Param("id") String id);

	int deleteCallbackLogs(@Param("id") String id);

	int deleteFeedbackDayLogs(@Param("id") String id);

	int deleteFeedbackLogs(@Param("id") String id);

	int deleteFlowLogs(@Param("id") String id);

	int deleteRtaTaskBindings(@Param("id") String id);

	int deleteRtaUpstreams(@Param("id") String id);

	int deleteCreativeBindings(@Param("id") String id);

	int deleteCreatives(@Param("id") String id);

	int deleteDeductionRules(@Param("id") String id);

	int deleteMonitorTasks(@Param("id") String id);

	int deletePromotionLinks(@Param("id") String id);

}
