

package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.myabtis.datascope.ShopDataScope;
import com.yzytech.scx.system.api.entity.SysMaterialGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 素材分组
 *
 * @since 2022/2/22 15:27
 */
@Mapper
public interface SysMaterialGroupMapper extends BaseMapper<SysMaterialGroup> {

	/**
	 * 分页查询
	 * @param page
	 * @param sysMaterialGroup
	 * @return
	 */
	@ShopDataScope
	IPage<SysMaterialGroup> selectMaterialGroupPage(Page page, @Param("query") SysMaterialGroup sysMaterialGroup);

	/**
	 * 查询全部
	 * @param sysMaterialGroup
	 * @return
	 */
	@ShopDataScope
	List<SysMaterialGroup> selectMaterialGroupList(@Param("query") SysMaterialGroup sysMaterialGroup);

}
