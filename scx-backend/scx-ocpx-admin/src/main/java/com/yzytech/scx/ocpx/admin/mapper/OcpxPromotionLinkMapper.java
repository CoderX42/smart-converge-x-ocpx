package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxPromotionLinkMapper extends BaseMapper<OcpxPromotionLink> {

	long countCreativeBindings(@Param("id") String id);

	int deleteCreativeBindings(@Param("id") String id);

}
