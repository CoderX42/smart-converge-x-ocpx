package com.yzytech.scx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.inventory.entity.Placement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlacementMapper extends BaseMapper<Placement> {

	@Select("""
			SELECT
			  (SELECT COUNT(*) FROM scx_placement_quality_snapshot WHERE placement_id = #{id})
			  + (SELECT COUNT(*) FROM scx_media_publishing_binding WHERE placement_id = #{id})
			""")
	long countReferences(@Param("id") String id);

}
