

package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.common.core.constant.CacheConstants;
import com.yzytech.scx.system.api.entity.SysDict;
import com.yzytech.scx.system.api.entity.SysDictValue;
import com.yzytech.scx.system.mapper.SysDictMapper;
import com.yzytech.scx.system.mapper.SysDictValueMapper;
import com.yzytech.scx.system.service.ISysDictValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 字典键值
 *
 * @date 2022/10/21
 */
@Service
@RequiredArgsConstructor
public class SysDictValueServiceImpl extends ServiceImpl<SysDictValueMapper, SysDictValue>
		implements ISysDictValueService {

	private final SysDictMapper sysDictMapper;

	@Override
	@CacheEvict(value = CacheConstants.DICT_CACHE, allEntries = true)
	public boolean saveDictValue(SysDictValue sysDictValue) {
		SysDict sysDict = sysDictMapper.selectById(sysDictValue.getDictId());
		if (Objects.isNull(sysDict)) {
			throw new IllegalArgumentException("字典不存在");
		}
		sysDictValue.setDictType(sysDict.getType());
		return this.save(sysDictValue);
	}

}
