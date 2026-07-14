

package com.yzytech.scx.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.system.api.entity.SysAgreement;
import com.yzytech.scx.system.mapper.SysAgreementMapper;
import com.yzytech.scx.system.service.ISysAgreementService;
import org.springframework.stereotype.Service;

/**
 * 系统协议
 *
 * @date 2025/5/26
 */
@Service
public class SysAgreementServiceImpl extends ServiceImpl<SysAgreementMapper, SysAgreement>
		implements ISysAgreementService {

	@Override
	public boolean saveAgreement(SysAgreement sysAgreement) {
		if (StrUtil.isBlank(sysAgreement.getType())) {
			throw new BootBusinessException("协议类型不能为空");
		}
		if (existsSameType(sysAgreement.getType(), null)) {
			throw new BootBusinessException("该协议类型已存在");
		}
		return save(sysAgreement);
	}

	@Override
	public boolean updateAgreement(SysAgreement sysAgreement) {
		if (StrUtil.isBlank(sysAgreement.getId())) {
			throw new BootBusinessException("主键不能为空");
		}
		if (StrUtil.isBlank(sysAgreement.getType())) {
			throw new BootBusinessException("协议类型不能为空");
		}
		if (existsSameType(sysAgreement.getType(), sysAgreement.getId())) {
			throw new BootBusinessException("该协议类型已存在");
		}
		return updateById(sysAgreement);
	}

	/**
	 * @param excludeId 编辑时排除当前记录；新增传 null
	 */
	private boolean existsSameType(String type, String excludeId) {
		LambdaQueryWrapper<SysAgreement> w = Wrappers.<SysAgreement>lambdaQuery().eq(SysAgreement::getType, type);
		if (StrUtil.isNotBlank(excludeId)) {
			w.ne(SysAgreement::getId, excludeId);
		}
		return count(w) > 0;
	}

}
