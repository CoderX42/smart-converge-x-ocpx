package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.MediaRequests;
import com.yzytech.scx.ocpx.admin.dto.MediaViews;
import com.yzytech.scx.ocpx.admin.dto.ManagementRows;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OcpxMediaService {

	private final OcpxMediaMapper mediaMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final AdvertiserScope advertiserScope;

	public IPage<MediaViews.Item> list(Page<OcpxMedia> page, String keyword, String mediaId) {
		advertiserScope.requireAdministrator();
		return mediaMapper
			.selectPage(page, Wrappers.<OcpxMedia>lambdaQuery()
				.eq(StringUtils.hasText(mediaId), OcpxMedia::getId, mediaId)
				.and(StringUtils.hasText(keyword) && !StringUtils.hasText(mediaId),
						q -> q.like(OcpxMedia::getId, keyword).or().like(OcpxMedia::getMediaName, keyword))
				.orderByDesc(OcpxMedia::getUpdateTime))
			.convert(this::view);
	}

	/** 对齐 SGYZ getChannelName：下拉只返回渠道编号和名称。 */
	public List<MediaViews.Option> options() {
		String scopedAdvertiserId = advertiserScope.currentAdvertiserId();
		if (StringUtils.hasText(scopedAdvertiserId)) {
			return mediaMapper.selectOptionsForAdvertiser(scopedAdvertiserId)
				.stream()
				.map(item -> new MediaViews.Option(item.getId(), item.getName()))
				.toList();
		}
		return mediaMapper.selectList(Wrappers.<OcpxMedia>lambdaQuery().orderByDesc(OcpxMedia::getId))
			.stream()
			.map(item -> new MediaViews.Option(item.getId(), item.getMediaName()))
			.toList();
	}

	public MediaViews.Item detail(String id) {
		advertiserScope.requireAdministrator();
		return view(require(id));
	}

	@Transactional
	public MediaViews.Item create(MediaRequests.Create request) {
		advertiserScope.requireAdministrator();
		ensureUnique(null, request.mediaName());
		OcpxMedia media = new OcpxMedia();
		apply(media, request.mediaName(), request.credentialConfigKey(), request.uri(), request.macroParams());
		mediaMapper.insert(media);
		configRefreshPublisher.mark();
		return detail(media.getId());
	}

	@Transactional
	public MediaViews.Item update(String id, MediaRequests.Update request) {
		advertiserScope.requireAdministrator();
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
