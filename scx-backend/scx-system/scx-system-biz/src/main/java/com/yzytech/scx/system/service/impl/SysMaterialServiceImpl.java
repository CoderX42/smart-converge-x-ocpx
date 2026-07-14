

package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.system.api.entity.SysMaterial;
import com.yzytech.scx.system.mapper.SysMaterialMapper;
import com.yzytech.scx.system.service.ISysMaterialService;
import org.springframework.stereotype.Service;

/**
 * 素材
 *
 * @since 2022/2/26 16:37
 */
@Service
public class SysMaterialServiceImpl extends ServiceImpl<SysMaterialMapper, SysMaterial> implements ISysMaterialService {

	@Override
	public IPage<SysMaterial> getPage(Page page, SysMaterial material) {
		return baseMapper.selectMaterialPage(page, material);
	}

}
