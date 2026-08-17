package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OcpxPromotionLinkMapper extends BaseMapper<OcpxPromotionLink> {

	@Select("SELECT COUNT(*) FROM scx_ocpx_creative_promotion_link WHERE promotion_link_id=#{id}")
	long countCreativeBindings(@Param("id") String id);

	@Delete("DELETE FROM scx_ocpx_creative_promotion_link WHERE promotion_link_id=#{id}")
	int deleteCreativeBindings(@Param("id") String id);

}
