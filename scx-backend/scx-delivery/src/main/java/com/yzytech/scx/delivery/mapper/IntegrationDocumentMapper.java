package com.yzytech.scx.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.delivery.entity.IntegrationDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntegrationDocumentMapper extends BaseMapper<IntegrationDocument> {

	@Select("SELECT * FROM scx_integration_document WHERE id = #{id} FOR UPDATE")
	IntegrationDocument selectForUpdate(@Param("id") String id);

	@Select("""
			SELECT
			  (SELECT COUNT(*) FROM scx_media_channel WHERE document_id = #{id})
			  + (SELECT COUNT(*) FROM scx_tracking_link WHERE document_id = #{id})
			  + (SELECT COUNT(*) FROM scx_report_daily WHERE document_id = #{id})
			""")
	long countReferences(@Param("id") String id);

}
