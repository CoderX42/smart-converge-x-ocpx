

package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.myabtis.datascope.ShopDataScope;
import com.yzytech.scx.system.api.entity.SysMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 素材
 *
 * @since 2022/2/22 15:02
 */
@Mapper
public interface SysMaterialMapper extends BaseMapper<SysMaterial> {

	/**
	 * 分页查询
	 * @param page
	 * @param material
	 * @return
	 */
	@ShopDataScope
	IPage<SysMaterial> selectMaterialPage(Page page, @Param("query") SysMaterial material);

}
