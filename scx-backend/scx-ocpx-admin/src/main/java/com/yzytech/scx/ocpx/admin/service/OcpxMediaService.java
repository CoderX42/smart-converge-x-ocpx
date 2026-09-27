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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OcpxMediaService {

	private final OcpxMediaMapper mediaMapper;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final AdvertiserScope advertiserScope;

	public IPage<MediaViews.Item> list(Page<OcpxMedia> page, String keyword, String mediaId) {
		advertiserScope.requireAdministrator();
		return mediaMapper
			.selectPage(page,
					Wrappers.<OcpxMedia>lambdaQuery()
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
		ensureUnique(null, request.getMediaName());
		OcpxMedia media = new OcpxMedia();
		apply(media, request.getMediaName(), request.getCredentialConfigKey(), request.getUri(),
				request.getMacroParams());
		mediaMapper.insert(media);
		configRefreshPublisher.mark();
		return detail(media.getId());
	}

	@Transactional
	public MediaViews.Item update(String id, MediaRequests.Update request) {
		advertiserScope.requireAdministrator();
		OcpxMedia current = require(id);
		ensureUnique(id, request.getMediaName());
		OcpxMedia update = new OcpxMedia();
		update.setId(id);
		apply(update, request.getMediaName(), request.getCredentialConfigKey(), request.getUri(),
				request.getMacroParams(), current.getMacroParams());
		mediaMapper.updateById(update);
		configRefreshPublisher.mark();
		return detail(id);
	}

	@Transactional
	public void delete(String id) {
		advertiserScope.requireAdministrator();
		require(id);
		if (mediaMapper.countReferences(id) > 0) {
			throw new BootBusinessException("媒体仍有监测任务、扣量规则或历史数据，不能删除");
		}
		mediaMapper.deleteRtaAccess(id);
		if (mediaMapper.deleteById(id) != 1) {
			throw new IllegalStateException("媒体删除失败");
		}
		configRefreshPublisher.mark();
	}

	private void apply(OcpxMedia media, String name, String key, String uri, String macros) {
		apply(media, name, key, uri, macros, null);
	}

	private void apply(OcpxMedia media, String name, String key, String uri, String macros,
			String previousMacros) {
		media.setMediaName(name.trim());
		media.setCredentialConfigKey(trim(key));
		media.setUri(uri.trim());
		String normalized = trim(macros);
		validateMacroParams(normalized, previousMacros);
		media.setMacroParams(normalized);
	}

	private static final Pattern MACRO_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");

	/**
	 * Validate the compact query-string representation used by media links. New media use
	 * canonical catalog keys and exact macro values; non-derivable CAID slots are allowed
	 * only as explicit advanced extensions. Alias fields are accepted only when already
	 * present on the media being edited, keeping old production links byte-for-byte
	 * compatible while preventing new duplicate fields. Keys outside the catalog remain
	 * valid custom downstream parameters as long as they use the safe key=value syntax.
	 */
	private void validateMacroParams(String value, String previousValue) {
		if (!StringUtils.hasText(value)) {
			return;
		}
		Map<String, Integer> previousSegments = segmentCounts(previousValue);
		Map<String, Integer> previousKeys = keyCounts(previousValue);
		Map<String, Integer> seenKeys = new HashMap<>();
		for (String raw : value.split("&", -1)) {
			String segment = raw.trim();
			if (!StringUtils.hasText(segment)) {
				continue;
			}
			boolean exactPrevious = consume(previousSegments, segment);
			if (containsControlCharacter(segment)) {
				if (!exactPrevious) {
					throw invalidMacro("宏参数包含控制字符");
				}
				continue;
			}
			int separator = segment.indexOf('=');
			if (separator < 0) {
				if (!exactPrevious) {
					throw invalidMacro("宏参数必须使用 key=宏值 格式：" + segment);
				}
				continue;
			}
			String key = segment.substring(0, separator).trim();
			String macro = segment.substring(separator + 1).trim();
			String normalizedKey = key.toLowerCase(Locale.ROOT);
			if (!MACRO_KEY.matcher(normalizedKey).matches()) {
				if (!exactPrevious) {
					throw invalidMacro("宏参数 key 无效：" + key);
				}
				continue;
			}
			if (!key.equals(normalizedKey) && !exactPrevious) {
				throw invalidMacro("宏参数 key 必须使用小写：" + key);
			}
			if (MediaMacroCatalog.isReservedKey(normalizedKey)) {
				if (!exactPrevious) {
					throw invalidMacro("宏参数由系统自动维护，不能配置：" + key);
				}
				continue;
			}
			if (MediaMacroCatalog.isRetiredKey(normalizedKey)) {
				if (!exactPrevious) {
					throw invalidMacro("宏参数已下线，请使用秒时间戳 ts：" + key);
				}
				continue;
			}
			if (MediaMacroCatalog.isLegacyKey(normalizedKey)) {
				if (!MediaMacroCatalog.isAdvancedLegacyKey(normalizedKey) && !exactPrevious) {
					throw invalidMacro("新媒体不能配置兼容字段：" + key);
				}
				String expectedLegacy = MediaMacroCatalog.expectedMacro(normalizedKey);
				if (!expectedLegacy.equals(macro) && !exactPrevious) {
					throw invalidMacro("宏参数值与 key 不匹配：" + key);
				}
				int seen = seenKeys.merge(normalizedKey, 1, Integer::sum);
				if (seen > 1 && previousKeys.getOrDefault(normalizedKey, 0) < seen) {
					throw invalidMacro("宏参数 key 重复：" + key);
				}
				continue;
			}
			if (!MediaMacroCatalog.isCanonicalKey(normalizedKey)) {
				int seen = seenKeys.merge(normalizedKey, 1, Integer::sum);
				if (seen > 1 && previousKeys.getOrDefault(normalizedKey, 0) < seen) {
					throw invalidMacro("宏参数 key 重复：" + key);
				}
				continue;
			}
			String expected = MediaMacroCatalog.expectedMacro(normalizedKey);
			if (!expected.equals(macro) && !exactPrevious) {
				throw invalidMacro("宏参数值与 key 不匹配：" + key);
			}
			int seen = seenKeys.merge(normalizedKey, 1, Integer::sum);
			if (seen > 1 && previousKeys.getOrDefault(normalizedKey, 0) < seen) {
				throw invalidMacro("宏参数 key 重复：" + key);
			}
		}
	}

	private Map<String, Integer> segmentCounts(String value) {
		Map<String, Integer> counts = new HashMap<>();
		if (!StringUtils.hasText(value)) {
			return counts;
		}
		for (String segment : value.split("&", -1)) {
			String normalized = segment.trim();
			if (StringUtils.hasText(normalized)) {
				counts.merge(normalized, 1, Integer::sum);
			}
		}
		return counts;
	}

	private Map<String, Integer> keyCounts(String value) {
		Map<String, Integer> counts = new HashMap<>();
		Map<String, Integer> segments = segmentCounts(value);
		for (String segment : segments.keySet()) {
			int separator = segment.indexOf('=');
			String key = (separator < 0 ? segment : segment.substring(0, separator)).trim()
				.toLowerCase(Locale.ROOT);
			counts.merge(key, segments.get(segment), Integer::sum);
		}
		return counts;
	}

	private boolean consume(Map<String, Integer> counts, String key) {
		Integer count = counts.get(key);
		if (count == null) {
			return false;
		}
		if (count <= 1) {
			counts.remove(key);
		}
		else {
			counts.put(key, count - 1);
		}
		return true;
	}

	private boolean containsControlCharacter(String value) {
		return value.chars().anyMatch(character -> character < 0x20 || character == 0x7f);
	}

	private BootBusinessException invalidMacro(String message) {
		return new BootBusinessException("媒体宏参数校验失败：" + message);
	}

	private void ensureUnique(String id, String name) {
		if (mediaMapper.selectCount(Wrappers.<OcpxMedia>lambdaQuery()
			.eq(OcpxMedia::getMediaName, name.trim())
			.ne(StringUtils.hasText(id), OcpxMedia::getId, id)) > 0)
			throw new BootBusinessException("媒体名称已存在");
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
