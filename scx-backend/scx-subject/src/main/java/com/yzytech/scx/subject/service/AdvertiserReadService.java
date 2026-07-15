package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.subject.entity.Advertiser;
import com.yzytech.scx.subject.mapper.AdvertiserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Applies subject scope in the data access path. No caller receives an
 * advertiser merely because its identifier was guessed.
 */
@Service
@RequiredArgsConstructor
public class AdvertiserReadService {

	private static final String ADMIN_ROLE = "ROLE_ADMIN";
	private final AdvertiserMapper advertiserMapper;

	public List<Advertiser> listForCurrentUser() {
		BootUser user = SecurityUtils.getUser();
		if (user.getRoles().contains(ADMIN_ROLE)) {
			return advertiserMapper.selectList(Wrappers.<Advertiser>lambdaQuery()
				.eq(Advertiser::getStatus, "ACTIVE").orderByAsc(Advertiser::getName));
		}
		return advertiserMapper.findAuthorized(user.getUserId());
	}

	public Optional<Advertiser> getForCurrentUser(String advertiserId) {
		BootUser user = SecurityUtils.getUser();
		if (user.getRoles().contains(ADMIN_ROLE)) {
			return Optional.ofNullable(advertiserMapper.selectById(advertiserId))
				.filter(advertiser -> "ACTIVE".equals(advertiser.getStatus()));
		}
		return advertiserMapper.findAuthorizedById(user.getUserId(), advertiserId);
	}
}
