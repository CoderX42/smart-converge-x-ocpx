

package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.system.api.entity.SysSocialAccount;
import com.yzytech.scx.system.api.vo.SysSocialAccountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 三方平台账号
 *
 * @date 2025/8/9
 */
@Mapper
public interface SysSocialAccountMapper extends BaseMapper<SysSocialAccount> {

	/**
	 * 分页查询
	 * @param page
	 * @param sysSocialAccount
	 * @return
	 */
	IPage<SysSocialAccountVO> selectAccount(Page page, @Param("query") SysSocialAccount sysSocialAccount);

}
