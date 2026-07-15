package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.mapper.MediaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MediaReadService {

	private static final String ADMIN_ROLE = "ROLE_ADMIN";
	private final MediaMapper mediaMapper;

	public List<Media> listForCurrentUser() {
		BootUser user = SecurityUtils.getUser();
		if (user.getRoles().contains(ADMIN_ROLE)) {
			return mediaMapper.selectList(Wrappers.<Media>lambdaQuery()
				.eq(Media::getStatus, "ACTIVE").orderByAsc(Media::getName));
		}
		return mediaMapper.findAuthorized(user.getUserId());
	}

	public Optional<Media> getForCurrentUser(String mediaId) {
		BootUser user = SecurityUtils.getUser();
		if (user.getRoles().contains(ADMIN_ROLE)) {
			return Optional.ofNullable(mediaMapper.selectById(mediaId))
				.filter(media -> "ACTIVE".equals(media.getStatus()));
		}
		return mediaMapper.findAuthorizedById(user.getUserId(), mediaId);
	}
}
