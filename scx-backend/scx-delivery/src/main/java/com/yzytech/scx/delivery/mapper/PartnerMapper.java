package com.yzytech.scx.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.delivery.entity.Partner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PartnerMapper extends BaseMapper<Partner> {

	@Select("SELECT * FROM scx_partner WHERE id = #{id} FOR UPDATE")
	Partner selectForUpdate(@Param("id") String id);

	@Select("""
			SELECT
			  (SELECT COUNT(*) FROM scx_product WHERE partner_id = #{id})
			  + (SELECT COUNT(*) FROM scx_tracking_link WHERE partner_id = #{id})
			  + (SELECT COUNT(*) FROM scx_report_daily WHERE partner_id = #{id})
			""")
	long countReferences(@Param("id") String id);

}
