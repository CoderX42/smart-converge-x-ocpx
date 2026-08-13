package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.MediaRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaViews;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service
@RequiredArgsConstructor
public class OcpxMediaService {

	private final OcpxMediaMapper mediaMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	public IPage<MediaViews.Item> list(Page<OcpxMedia> page, String keyword) {
		return mediaMapper
			.selectPage(page, Wrappers.<OcpxMedia>lambdaQuery()
				.and(StringUtils.hasText(keyword),
						q -> q.like(OcpxMedia::getId, keyword).or().like(OcpxMedia::getMediaName, keyword))
				.orderByDesc(OcpxMedia::getUpdateTime))
			.convert(this::view);
	}

	public MediaViews.Item detail(String id) {
		return view(require(id));
	}

	@Transactional
	public MediaViews.Item create(MediaRequests.Create request) {
		ensureUnique(null, request.mediaName());
		OcpxMedia media = new OcpxMedia();
		apply(media, request.mediaName(), request.credentialConfigKey(), request.uri(), request.macroParams());
		mediaMapper.insert(media);
		configRefreshPublisher.mark();
		return detail(media.getId());
	}

	@Transactional
	public MediaViews.Item update(String id, MediaRequests.Update request) {
		require(id);
		ensureUnique(id, request.mediaName());
		OcpxMedia update = new OcpxMedia();
		update.setId(id);
		apply(update, request.mediaName(), request.credentialConfigKey(), request.uri(), request.macroParams());
		mediaMapper.updateById(update);
		configRefreshPublisher.mark();
		return detail(id);
	}

	private void apply(OcpxMedia media, String name, String key, String uri, String macros) {
		media.setMediaName(name.trim());
		media.setCredentialConfigKey(trim(key));
		media.setUri(uri.trim());
		media.setMacroParams(trim(macros));
	}

	private void ensureUnique(String id, String name) {
		if (mediaMapper.selectCount(Wrappers.<OcpxMedia>lambdaQuery()
			.eq(OcpxMedia::getMediaName, name.trim())
			.ne(StringUtils.hasText(id), OcpxMedia::getId, id)) > 0)
			throw new IllegalArgumentException("媒体名称已存在");
	}

	private OcpxMedia require(String id) {
		OcpxMedia value = mediaMapper.selectById(id);
		if (value == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "媒体不存在");
		return value;
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private MediaViews.Item view(OcpxMedia m) {
		return new MediaViews.Item(m.getId(), m.getMediaName(), m.getCredentialConfigKey(), m.getUri(),
				m.getMacroParams(), m.getCreateTime(), m.getUpdateTime());
	}

}
