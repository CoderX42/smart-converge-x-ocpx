

package com.yzytech.scx.system.service;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysDept;

import java.util.List;

/**
 * 部门
 *
 * @since 2022/2/26 16:47
 */
public interface ISysDeptService extends IService<SysDept> {

	List<Tree<String>> getTreeList();

}
