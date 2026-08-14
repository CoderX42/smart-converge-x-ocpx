package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.admin.dto.ManagementRows;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OcpxMediaMapper extends BaseMapper<OcpxMedia> {

	@Select("""
			SELECT CASE WHEN
			  EXISTS(SELECT 1 FROM scx_ocpx_monitor_task WHERE media_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_deduction_rule WHERE media_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_flow_log WHERE media_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_log_feedback WHERE m_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_log_feedback_day WHERE m_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_log_callback WHERE m_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_report_mat_temp WHERE m_id=#{id})
			  OR EXISTS(SELECT 1 FROM scx_ocpx_report_mat WHERE m_id=#{id})
			THEN 1 ELSE 0 END
			""")
	int countReferences(@Param("id") String id);

	@Select("""
			SELECT DISTINCT m.id,m.media_name AS name
			FROM scx_ocpx_media m
			JOIN scx_ocpx_monitor_task t ON t.media_id=m.id AND t.is_delete=0
			WHERE t.advertiser_id=#{advertiserId}
			ORDER BY m.id DESC
			""")
	List<ManagementRows.NamedOption> selectOptionsForAdvertiser(@Param("advertiserId") String advertiserId);

}
