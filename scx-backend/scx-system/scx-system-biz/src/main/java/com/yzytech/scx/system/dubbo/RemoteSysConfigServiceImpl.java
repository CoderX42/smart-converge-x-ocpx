

package com.yzytech.scx.system.dubbo;

import com.yzytech.scx.system.api.remote.RemoteSysConfigService;
import com.yzytech.scx.system.service.ISysConfigService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSysConfigServiceImpl implements RemoteSysConfigService {

	private final ISysConfigService sysConfigService;

	@Override
	public String getSysConfig(String key) {
		return sysConfigService.getConfigValue(key);
	}

}
