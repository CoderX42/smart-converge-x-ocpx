package com.yzytech.scx.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.subject.entity.Media;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaMapper extends BaseMapper<Media> {

	@Select("""
			SELECT
			  (SELECT COUNT(*) FROM scx_user_subject_scope
			    WHERE subject_type = 'MEDIA' AND subject_id = #{id})
			  + (SELECT COUNT(*) FROM scx_partner WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_upstream_customer WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_product WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_integration_document WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_media_channel WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_media_channel_user WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_tracking_link WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_tracking_task WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_report_daily WHERE media_id = #{id})
			  + (SELECT COUNT(*) FROM scx_media_account WHERE media_id = #{id})
			""")
	long countReferences(@Param("id") String id);

}
