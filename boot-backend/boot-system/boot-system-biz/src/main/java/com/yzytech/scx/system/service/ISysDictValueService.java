

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysDictValue;

/**
 * 字典键值
 *
 * @date 2022/10/21
 */
public interface ISysDictValueService extends IService<SysDictValue> {

	/**
	 * 新增字典值
	 * @param sysDictValue
	 * @return
	 */
	boolean saveDictValue(SysDictValue sysDictValue);

}
