package com.yzytech.scx.delivery.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.delivery.entity.MediaChannel;
import com.yzytech.scx.delivery.entity.MediaChannelUser;
import com.yzytech.scx.delivery.dto.MediaChannelUserView;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.delivery.mapper.MediaChannelUserMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MediaChannelUserService {

	private final MediaChannelUserMapper mapper;

	private final MediaChannelMapper channelMapper;

	private final MediaScopeService scopeService;

	public IPage<MediaChannelUserView> page(Page<MediaChannelUser> p, String mediaId, String channelId, String keyword,
			String status) {
		read(mediaId);
		return mapper.selectPage(p, Wrappers.<MediaChannelUser>lambdaQuery()
			.eq(MediaChannelUser::getMediaId, mediaId)
			.eq(StringUtils.hasText(channelId), MediaChannelUser::getChannelId, channelId)
			.eq(StringUtils.hasText(status), MediaChannelUser::getStatus, status)
			.and(StringUtils.hasText(keyword),
					q -> q.like(MediaChannelUser::getAccount, keyword).or().like(MediaChannelUser::getName, keyword))
			.orderByDesc(MediaChannelUser::getCreateTime))
			.convert(MediaChannelUserView::masked);
	}

	public MediaChannelUserView sensitive(String id, String mediaId) {
		read(mediaId);
		MediaChannelUser value = mapper.selectById(id);
		if (value == null || !mediaId.equals(value.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		return MediaChannelUserView.sensitive(value);
	}

	@Transactional
	public boolean save(MediaChannelUser v) {
		write(v.getMediaId());
		MediaChannel channel = channelMapper.selectById(v.getChannelId());
		if (channel == null || !v.getMediaId().equals(channel.getMediaId()))
			throw new IllegalArgumentException("投放渠道不在当前范围");
		if (v.getId() != null) {
			MediaChannelUser old = mapper.selectById(v.getId());
			if (old == null || !old.getMediaId().equals(v.getMediaId()))
				throw new IllegalArgumentException("资源不存在");
		}
		if (v.getStatus() == null)
			v.setStatus("ACTIVE");
		if (v.getVersion() == null)
			v.setVersion(0);
		return v.getId() == null ? mapper.insert(v) == 1 : mapper.updateById(v) == 1;
	}

	@Transactional
	public boolean delete(String id, String mediaId, Integer version) {
		write(mediaId);
		MediaChannelUser existing = mapper.selectById(id);
		if (existing == null || !mediaId.equals(existing.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		if (!Objects.equals(existing.getVersion(), version))
			throw new IllegalStateException("渠道用户已被其他用户修改，请刷新后重试");
		int deleted = mapper.delete(Wrappers.<MediaChannelUser>lambdaQuery()
			.eq(MediaChannelUser::getId, id)
			.eq(MediaChannelUser::getVersion, version));
		if (deleted != 1)
			throw new IllegalStateException("渠道用户已被其他用户修改，请刷新后重试");
		return true;
	}

	private void read(String id) {
		if (id == null || !scopeService.canRead(id))
			throw new IllegalArgumentException("资源不存在");
	}

	private void write(String id) {
		if (id == null || !scopeService.canWrite(id))
			throw new IllegalArgumentException("资源不存在");
	}

}
