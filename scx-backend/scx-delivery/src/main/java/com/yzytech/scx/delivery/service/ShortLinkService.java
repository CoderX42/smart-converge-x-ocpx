package com.yzytech.scx.delivery.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.delivery.dto.DpConversionRequest;
import com.yzytech.scx.delivery.dto.DpConversionResult;
import com.yzytech.scx.delivery.entity.ShortLink;
import com.yzytech.scx.delivery.mapper.ShortLinkMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ShortLinkService {

	private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

	private static final Set<String> TYPES = Set.of("GENERAL", "TAOBAO_DP", "JD_DP");

	private static final Map<String, Set<String>> DP_HOSTS = Map.of("TAOBAO_DP",
			Set.of("uland.taobao.com", "item.taobao.com", "detail.tmall.com"), "JD_DP",
			Set.of("union-click.jd.com", "item.jd.com"));

	private static final SecureRandom RANDOM = new SecureRandom();

	private final ShortLinkMapper mapper;

	private final MediaScopeService scopeService;

	private final MasterDataService masterDataService;

	public Page<ShortLink> page(Page<ShortLink> page, String mediaId, String keyword, String status) {
		requireRead(mediaId);
		return mapper.selectPage(page,
				Wrappers.<ShortLink>lambdaQuery()
					.eq(ShortLink::getMediaId, mediaId)
					.eq(StringUtils.hasText(status), ShortLink::getStatus, status)
					.and(StringUtils.hasText(keyword),
							q -> q.like(ShortLink::getCode, keyword).or().like(ShortLink::getName, keyword))
					.orderByDesc(ShortLink::getCreateTime));
	}

	@Transactional
	public boolean save(ShortLink value) {
		requireWrite(value.getMediaId());
		if (value.getId() != null) {
			ShortLink existing = mapper.selectById(value.getId());
			if (existing == null || !existing.getMediaId().equals(value.getMediaId())
					|| !scopeService.canWrite(existing.getMediaId()))
				throw new IllegalArgumentException("资源不存在");
		}
		String type = normalizeType(value.getLinkType());
		if ("GENERAL".equals(type)) {
			masterDataService.validateSandboxUrl(value.getTargetUrl());
			value.setSourceUrl(null);
		}
		else {
			String sourceUrl = StringUtils.hasText(value.getSourceUrl()) ? value.getSourceUrl() : value.getTargetUrl();
			DpConversionRequest request = new DpConversionRequest();
			request.setLinkType(type);
			request.setSourceUrl(sourceUrl);
			DpConversionResult conversion = convertDp(request);
			value.setSourceUrl(conversion.sourceUrl());
			value.setTargetUrl(conversion.targetUrl());
		}
		value.setLinkType(type);
		if (value.getStatus() == null)
			value.setStatus("ACTIVE");
		if (value.getVersion() == null)
			value.setVersion(0);
		if (!StringUtils.hasText(value.getCode()))
			value.setCode(nextCode());
		return value.getId() == null ? mapper.insert(value) == 1 : mapper.updateById(value) == 1;
	}

	public String resolve(String code) {
		ShortLink link = mapper.selectOne(Wrappers.<ShortLink>lambdaQuery()
			.eq(ShortLink::getCode, code)
			.eq(ShortLink::getStatus, "ACTIVE")
			.last("LIMIT 1"));
		if (link == null)
			throw new IllegalArgumentException("短链不存在或已停用");
		return link.getTargetUrl();
	}

	/**
	 * DP links are normalized locally rather than exchanged through a vendor API. This
	 * validates the redirect destination before it can be persisted, while keeping future
	 * vendor adapters outside the public short-link redirect path.
	 */
	public DpConversionResult convertDp(DpConversionRequest request) {
		String type = normalizeType(request.getLinkType());
		if ("GENERAL".equals(type))
			throw new IllegalArgumentException("仅支持淘宝或京东 DP 转换");
		String sourceUrl = request.getSourceUrl().trim();
		if (sourceUrl.length() > 1000)
			throw new IllegalArgumentException("DP 地址过长");
		URI uri;
		try {
			uri = URI.create(sourceUrl);
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("DP 地址格式无效");
		}
		String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
				|| host == null || !DP_HOSTS.get(type).contains(host)) {
			throw new IllegalArgumentException(
					type.equals("TAOBAO_DP") ? "仅允许淘宝 DP 白名单 HTTPS 地址" : "仅允许京东 DP 白名单 HTTPS 地址");
		}
		String path = uri.normalize().getRawPath();
		if (!StringUtils.hasText(path) || path.contains("..") || path.length() > 500) {
			throw new IllegalArgumentException("DP 地址路径无效");
		}
		List<QueryItem> queryItems = parseAndValidateQuery(uri.getRawQuery());
		if (queryItems.isEmpty())
			throw new IllegalArgumentException("DP 地址缺少推广参数");
		String query = queryItems.stream()
			.sorted(Comparator.comparing(QueryItem::key).thenComparing(QueryItem::value))
			.map(item -> encode(item.key()) + "=" + encode(item.value()))
			.reduce((left, right) -> left + "&" + right)
			.orElse("");
		String targetUrl = "https://" + host + path + "?" + query;
		if (targetUrl.length() > 1000)
			throw new IllegalArgumentException("DP 地址过长");
		return new DpConversionResult(type, sourceUrl, targetUrl);
	}

	private List<QueryItem> parseAndValidateQuery(String rawQuery) {
		if (!StringUtils.hasText(rawQuery))
			return List.of();
		List<QueryItem> values = new ArrayList<>();
		for (String item : rawQuery.split("&")) {
			String[] pair = item.split("=", 2);
			String key = decode(pair[0]);
			String value = pair.length == 2 ? decode(pair[1]) : "";
			if (!key.matches("[A-Za-z0-9_.-]{1,80}") || key.toLowerCase(Locale.ROOT).startsWith("scx_")
					|| value.length() > 700 || value.contains("\r") || value.contains("\n")) {
				throw new IllegalArgumentException("DP 推广参数不合法");
			}
			values.add(new QueryItem(key, value));
		}
		return values;
	}

	private String normalizeType(String value) {
		String type = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "GENERAL";
		if (!TYPES.contains(type))
			throw new IllegalArgumentException("不支持的短链类型");
		return type;
	}

	private String decode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("DP 推广参数编码无效");
		}
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private record QueryItem(String key, String value) {
	}

	private String nextCode() {
		for (int attempt = 0; attempt < 8; attempt++) {
			StringBuilder code = new StringBuilder(8);
			for (int i = 0; i < 8; i++)
				code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
			if (mapper.selectCount(Wrappers.<ShortLink>lambdaQuery().eq(ShortLink::getCode, code.toString())) == 0)
				return code.toString();
		}
		throw new IllegalArgumentException("短链编码生成失败，请重试");
	}

	private void requireRead(String mediaId) {
		if (mediaId == null || !scopeService.canRead(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

	private void requireWrite(String mediaId) {
		if (mediaId == null || !scopeService.canWrite(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

}
