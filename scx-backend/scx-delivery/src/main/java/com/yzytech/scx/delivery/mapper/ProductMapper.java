package com.yzytech.scx.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.delivery.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

	@Select("SELECT * FROM scx_product WHERE id = #{id} FOR UPDATE")
	Product selectForUpdate(@Param("id") String id);

	@Select("""
			SELECT
			  (SELECT COUNT(*) FROM scx_tracking_link WHERE product_id = #{id})
			  + (SELECT COUNT(*) FROM scx_report_daily WHERE product_id = #{id})
			""")
	long countReferences(@Param("id") String id);

}
