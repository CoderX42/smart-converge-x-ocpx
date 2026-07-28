package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.subject.dto.MediaCreateRequest;
import com.yzytech.scx.subject.dto.MediaStatusRequest;
import com.yzytech.scx.subject.dto.MediaUpdateRequest;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.mapper.MediaMapper;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MediaManagementService {

	private static final String ACTIVE = "ACTIVE";

	private static final String DISABLED = "DISABLED";

	private static final String CNY = "CNY";

	private static final Set<String> STATUSES = Set.of(ACTIVE, DISABLED);

	private final MediaMapper mediaMapper;

	public Page<Media> page(Page<Media> page, String keyword, String status) {
		if (page == null) {
			page = new Page<>();
		}
		if (StringUtils.hasText(status) && !STATUSES.contains(status)) {
			throw new IllegalArgumentException("无效媒体状态");
		}
		return mediaMapper.selectPage(page, Wrappers.<Media>lambdaQuery()
			.and(StringUtils.hasText(keyword), q -> q.like(Media::getCode, keyword).or().like(Media::getName, keyword))
			.eq(StringUtils.hasText(status), Media::getStatus, status)
			.orderByAsc(Media::getName));
	}

	@Transactional
	public Media create(MediaCreateRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("媒体请求无效");
		}
		validateTimezone(request.getTimezone());
		validateCurrency(request.getSettlementCurrency());
		Media media = new Media();
		media.setCode(request.getCode());
		media.setName(request.getName());
		media.setStatus(ACTIVE);
		media.setTimezone(request.getTimezone());
		media.setSettlementCurrency(request.getSettlementCurrency());
		media.setVersion(0);
		try {
			mediaMapper.insert(media);
		}
		catch (DuplicateKeyException e) {
			throw new IllegalArgumentException("媒体编码已存在");
		}
		return media;
	}

	@Transactional
	public Media update(String id, MediaUpdateRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("媒体请求无效");
		}
		validateTimezone(request.getTimezone());
		validateCurrency(request.getSettlementCurrency());
		validateVersion(request.getVersion());
		Media existing = required(id);
		Media update = new Media();
		update.setId(existing.getId());
		update.setName(request.getName());
		update.setTimezone(request.getTimezone());
		update.setSettlementCurrency(request.getSettlementCurrency());
		update.setVersion(request.getVersion());
		if (mediaMapper.updateById(update) != 1) {
			throw new IllegalStateException("媒体已被其他操作修改，请刷新后重试");
		}
		return required(id);
	}

	@Transactional
	public Media changeStatus(String id, MediaStatusRequest request) {
		if (request == null || !STATUSES.contains(request.getStatus())) {
			throw new IllegalArgumentException("无效媒体状态");
		}
		validateVersion(request.getVersion());
		Media existing = required(id);
		Media update = new Media();
		update.setId(existing.getId());
		update.setStatus(request.getStatus());
		update.setVersion(request.getVersion());
		if (mediaMapper.updateById(update) != 1) {
			throw new IllegalStateException("媒体已被其他操作修改，请刷新后重试");
		}
		return required(id);
	}

	private Media required(String id) {
		Media media = mediaMapper.selectById(id);
		if (media == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		return media;
	}

	private void validateTimezone(String timezone) {
		// ZoneId.of also accepts fixed offsets such as "+08:00".  Media timezone
		// is a business timezone and must be a named IANA region so report day
		// boundaries remain stable and auditable.
		if (!StringUtils.hasText(timezone) || !ZoneId.getAvailableZoneIds().contains(timezone)) {
			throw new IllegalArgumentException("无效时区");
		}
	}

	private void validateCurrency(String currency) {
		if (!CNY.equals(currency)) {
			throw new IllegalArgumentException("仅支持 CNY 结算");
		}
	}

	private void validateVersion(Integer version) {
		if (version == null || version < 0) {
			throw new IllegalArgumentException("媒体版本无效");
		}
	}

}
