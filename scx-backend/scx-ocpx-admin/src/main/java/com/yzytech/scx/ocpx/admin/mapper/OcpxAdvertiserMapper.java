package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.admin.dto.ManagementRows;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface OcpxAdvertiserMapper extends BaseMapper<OcpxAdvertiser> {

	@Select("""
			<script>
			SELECT a.id AS advertiser_id,u.username
			FROM scx_ocpx_advertiser a
			LEFT JOIN sys_user u ON u.id=a.account_user_id AND u.del_flag='0'
			WHERE a.id IN
			<foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
			</script>
			""")
	List<ManagementRows.AdvertiserUsername> selectUsernamesByAdvertiserIds(@Param("ids") Collection<String> ids);

	@Select("SELECT * FROM scx_ocpx_advertiser WHERE id=#{id} FOR UPDATE")
	OcpxAdvertiser selectByIdForUpdate(@Param("id") String id);

	@Select("""
			SELECT DISTINCT route.promotion_link_id,route.media_id
			FROM (
			  SELECT promotion_link_id,media_id
			  FROM scx_ocpx_monitor_task
			  WHERE advertiser_id=#{id}
			  UNION
			  SELECT r.promotion_link_id,r.media_id
			  FROM scx_ocpx_deduction_rule r
			  JOIN scx_ocpx_promotion_link p ON p.id=r.promotion_link_id
			  WHERE p.advertiser_id=#{id}
			) route
			""")
	List<ManagementRows.RouteKey> selectCascadeRoutes(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_report_mat_temp WHERE a_id=#{id}")
	int deleteReportMatTemp(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_report_mat WHERE a_id=#{id}")
	int deleteReportMat(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_log_callback WHERE a_id=#{id}")
	int deleteCallbackLogs(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_log_feedback_day WHERE a_id=#{id}")
	int deleteFeedbackDayLogs(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_log_feedback WHERE a_id=#{id}")
	int deleteFeedbackLogs(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_flow_log WHERE advertiser_id=#{id}")
	int deleteFlowLogs(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_creative_promotion_link WHERE advertiser_id=#{id}")
	int deleteCreativeBindings(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_creative WHERE advertiser_id=#{id}")
	int deleteCreatives(@Param("id") String id);

	@Delete("""
			DELETE r FROM scx_ocpx_deduction_rule r
			JOIN scx_ocpx_promotion_link p ON p.id=r.promotion_link_id
			WHERE p.advertiser_id=#{id}
			""")
	int deleteDeductionRules(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_monitor_task WHERE advertiser_id=#{id}")
	int deleteMonitorTasks(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_promotion_link WHERE advertiser_id=#{id}")
	int deletePromotionLinks(@Param("id") String id);

}
