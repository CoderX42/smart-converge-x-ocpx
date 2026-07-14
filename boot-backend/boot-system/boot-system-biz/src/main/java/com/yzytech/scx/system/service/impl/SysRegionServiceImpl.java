

package com.yzytech.scx.system.service.impl;

import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNode;
import cn.hutool.core.lang.tree.TreeUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.system.api.entity.SysRegion;
import com.yzytech.scx.system.mapper.SysRegionMapper;
import com.yzytech.scx.system.service.ISysRegionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 行政区划
 *
 * @date 2025/5/3
 */
@Service
public class SysRegionServiceImpl extends ServiceImpl<SysRegionMapper, SysRegion> implements ISysRegionService {

	@Override
	public List<Tree<String>> getTree(String parentId) {
		List<SysRegion> sysDepts = baseMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
			.eq(StringUtils.hasText(parentId), SysRegion::getParentId, parentId)
			.orderByAsc(SysRegion::getSort));
		List<TreeNode<String>> treeNodes = sysDepts.stream().map(dept -> {
			TreeNode<String> treeNode = new TreeNode();
			treeNode.setId(dept.getId());
			treeNode.setParentId(dept.getParentId());
			treeNode.setName(dept.getName());
			Map<String, Object> extra = new HashMap<>();
			extra.put("createTime", dept.getCreateTime());
			extra.put("sort", dept.getSort());
			extra.put("code", dept.getCode());
			extra.put("level", dept.getLevel());
			extra.put("status", dept.getStatus());
			treeNode.setExtra(extra);
			return treeNode;
		}).collect(Collectors.toList());
		return TreeUtil.build(treeNodes, CommonConstants.PARENT_ID);
	}

}
