package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.admin.dto.ManagementRows;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import org.apache.ibatis.annotations.Mapper;
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

	@Select("""
			SELECT CASE WHEN
			  EXISTS(SELECT 1 FROM scx_ocpx_promotion_link WHERE advertiser_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_monitor_task WHERE advertiser_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_flow_log WHERE advertiser_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_log_feedback WHERE a_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_log_feedback_day WHERE a_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_log_callback WHERE a_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_report_mat_temp WHERE a_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_report_mat WHERE a_id=#{id})
			THEN 1 ELSE 0 END
			""")
	int countReferences(@Param("id") String id);

}
