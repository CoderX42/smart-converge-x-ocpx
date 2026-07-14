

package com.yzytech.scx.system.service;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysRegion;

import java.util.List;

/**
 * 行政区划
 *
 * @date 2025/5/3
 */
public interface ISysRegionService extends IService<SysRegion> {

	List<Tree<String>> getTree(String parentId);

}
