

package com.yzytech.scx.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yzytech.scx.system.api.entity.SysSocialAccount;
import com.yzytech.scx.system.api.vo.SysSocialAccountVO;

/**
 * 三方平台账号
 *
 * @date 2025/8/9
 */
public interface ISysSocialAccountService extends IService<SysSocialAccount> {

	/**
	 * 分页查询
	 * @param page
	 * @param sysSocialAccount
	 * @return
	 */
	IPage<SysSocialAccountVO> getPage(Page page, SysSocialAccount sysSocialAccount);

	/**
	 * 查询详情
	 * @param id
	 * @return
	 */
	SysSocialAccountVO getByAccountId(String id);

	/**
	 * 修改
	 * @param sysSocialAccount
	 * @return
	 */
	boolean updateAccountById(SysSocialAccount sysSocialAccount);

}
