

package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.common.core.desensitization.KeyDesensitization;
import com.yzytech.scx.system.api.entity.SysSocialAccount;
import com.yzytech.scx.system.api.vo.SysSocialAccountVO;
import com.yzytech.scx.system.mapper.SysSocialAccountMapper;
import com.yzytech.scx.system.service.ISysSocialAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 三方平台账号
 *
 * @date 2025/8/9
 */
@Service
@RequiredArgsConstructor
public class SysSocialAccountServiceImpl extends ServiceImpl<SysSocialAccountMapper, SysSocialAccount>
		implements ISysSocialAccountService {

	private final KeyDesensitization keyDesensitization = new KeyDesensitization();

	@Override
	public IPage<SysSocialAccountVO> getPage(Page page, SysSocialAccount sysSocialAccount) {
		return baseMapper.selectAccount(page, sysSocialAccount);
	}

	@Override
	public SysSocialAccountVO getByAccountId(String id) {
		SysSocialAccount sysSocialAccount = baseMapper.selectById(id);
		if (Objects.nonNull(sysSocialAccount)) {
			SysSocialAccountVO sysSocialAccountVO = new SysSocialAccountVO();
			BeanUtils.copyProperties(sysSocialAccount, sysSocialAccountVO);
			return sysSocialAccountVO;
		}
		return null;
	}

	@Override
	public boolean updateAccountById(SysSocialAccount sysSocialAccount) {
		SysSocialAccount target = baseMapper.selectById(sysSocialAccount.getId());
		if (Objects.isNull(target)) {
			throw new RuntimeException("账号不存在");
		}
		if (target.getAppSecret().equals(sysSocialAccount.getAppSecret())) {
		}
		if (keyDesensitization.serialize(target.getAppSecret()).equals(sysSocialAccount.getAppSecret())) {
			sysSocialAccount.setAppSecret(null);
		}

		return baseMapper.updateById(sysSocialAccount) > 0;
	}

}
