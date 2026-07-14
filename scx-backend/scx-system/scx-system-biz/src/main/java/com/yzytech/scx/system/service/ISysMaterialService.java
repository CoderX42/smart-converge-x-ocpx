

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysMaterial;

/**
 * 素材
 *
 * @since 2022/2/26 16:37
 */
public interface ISysMaterialService extends IService<SysMaterial> {

	/**
	 * 分页查询
	 * @param page
	 * @param material
	 * @return
	 */
	IPage<SysMaterial> getPage(Page page, SysMaterial material);

}
