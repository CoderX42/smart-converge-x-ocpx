package com.yzytech.scx.adx.service;

import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdxAccessScope {

	public String currentAdvertiserId() {
		BootUser user = SecurityUtils.getUser();
		if (user == null || !"2".equals(user.getType())) {
			return null;
		}
		if (!StringUtils.hasText(user.getShopId())) {
			throw new IllegalStateException("广告主账号未绑定广告主");
		}
		return user.getShopId();
	}

	public String constrainAdvertiser(String requestedAdvertiserId) {
		String currentAdvertiserId = currentAdvertiserId();
		return currentAdvertiserId == null ? requestedAdvertiserId : currentAdvertiserId;
	}

}
