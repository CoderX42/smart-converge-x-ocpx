

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysAgreement;

/**
 * 系统协议
 *
 * @date 2025/5/31
 */
public interface ISysAgreementService extends IService<SysAgreement> {

	/**
	 * 新增协议
	 */
	boolean saveAgreement(SysAgreement sysAgreement);

	/**
	 * 修改协议
	 */
	boolean updateAgreement(SysAgreement sysAgreement);

}
