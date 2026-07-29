package com.yzytech.scx.system.service.impl;

import com.yzytech.scx.common.storage.handler.BootUploadFileHandler;
import com.yzytech.scx.common.storage.handler.StorageFactory;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import com.yzytech.scx.system.api.entity.SysStorageConfig;
import com.yzytech.scx.system.api.service.StorageUploadService;
import com.yzytech.scx.system.service.ISysStorageConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemStorageUploadService implements StorageUploadService {

	private final ISysStorageConfigService storageConfigService;

	private final StorageFactory storageFactory;

	@Override
	public ManagedStorageObject uploadManaged(InputStream inputStream, String fileName, String contentType, long size) {
		SysStorageConfigDTO config = storageConfigService.getConfig();
		if (config == null || !StringUtils.hasText(config.getType())) {
			throw new IllegalStateException("文件存储配置为空");
		}
		BootUploadFileHandler handler = storageFactory.getStrategy(config.getType());
		if (handler == null) {
			throw new IllegalStateException("当前文件存储类型不可用");
		}
		String url = handler.uploadFile(config, inputStream, fileName, contentType, size);
		String objectKey = resolveObjectKey(config, url, true);
		return new ManagedStorageObject(config.getId(), config.getType(), objectKey, url);
	}

	@Override
	public ManagedStorageObject resolve(String objectUrl) {
		if (!StringUtils.hasText(objectUrl)) {
			throw new IllegalStateException("存储对象地址为空");
		}
		List<SysStorageConfig> configs = storageConfigService.list();
		for (SysStorageConfig entity : configs) {
			SysStorageConfigDTO config = toDto(entity);
			try {
				String objectKey = resolveObjectKey(config, objectUrl, false);
				return new ManagedStorageObject(config.getId(), config.getType(), objectKey, objectUrl);
			}
			catch (IllegalStateException ignored) {
				// Try the next historical configuration. An object is accepted only when
				// its URL can be proven to belong to one configured storage namespace.
			}
		}
		throw new IllegalStateException("历史文档缺少可验证的存储定位，无法安全访问或删除");
	}

	@Override
	public InputStream open(ManagedStorageObject object) {
		SysStorageConfigDTO config = requireConfig(object);
		BootUploadFileHandler handler = requireHandler(config.getType());
		return handler.openFile(config, object.objectKey());
	}

	@Override
	public void delete(ManagedStorageObject object) {
		SysStorageConfigDTO config = requireConfig(object);
		BootUploadFileHandler handler = requireHandler(config.getType());
		handler.deleteFile(config, object.objectKey());
	}

	private SysStorageConfigDTO requireConfig(ManagedStorageObject object) {
		if (object == null || !StringUtils.hasText(object.storageConfigId())
				|| !StringUtils.hasText(object.objectKey())) {
			throw new IllegalStateException("存储对象引用不完整");
		}
		SysStorageConfig entity = storageConfigService.getById(object.storageConfigId());
		if (entity == null) {
			throw new IllegalStateException("文档对应的系统存储配置不存在");
		}
		if (StringUtils.hasText(object.provider()) && !object.provider().equals(entity.getType())) {
			throw new IllegalStateException("文档存储类型与系统配置不一致");
		}
		return toDto(entity);
	}

	private BootUploadFileHandler requireHandler(String type) {
		BootUploadFileHandler handler = storageFactory.getStrategy(type);
		if (handler == null) {
			throw new IllegalStateException("当前文件存储类型不可用");
		}
		return handler;
	}

	private String resolveObjectKey(SysStorageConfigDTO config, String objectUrl, boolean trustedUploadResult) {
		if (config == null || !StringUtils.hasText(config.getType()) || !StringUtils.hasText(objectUrl)) {
			throw new IllegalStateException("存储对象引用不完整");
		}
		URI uri;
		try {
			uri = URI.create(objectUrl);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("存储对象地址格式无效", exception);
		}
		if (uri.getHost() == null || uri.getUserInfo() != null) {
			throw new IllegalStateException("存储对象地址格式无效");
		}
		String path = uri.getPath() == null ? "" : uri.getPath();
		if ("local".equals(config.getType())) {
			String marker = "/file/local/";
			int markerIndex = path.indexOf(marker);
			if (markerIndex < 0) {
				throw new IllegalStateException("本地存储对象地址不属于当前配置");
			}
			if (!trustedUploadResult) {
				if (!StringUtils.hasText(config.getDomain()) || !sameAuthority(normalizeUri(config.getDomain()), uri)) {
					throw new IllegalStateException("历史本地存储对象地址无法匹配当前配置域名");
				}
			}
			return requireSafeObjectKey(path.substring(markerIndex + marker.length()));
		}
		if (!"oss".equals(config.getType())) {
			throw new IllegalStateException("不支持的系统存储类型");
		}
		if (StringUtils.hasText(config.getDomain())) {
			URI domain = normalizeUri(config.getDomain());
			if (!sameAuthority(domain, uri)) {
				throw new IllegalStateException("对象地址不属于当前自定义域名");
			}
			return requireSafeObjectKey(trimLeadingSlash(path));
		}
		URI endpoint = normalizeUri(config.getEndpoint());
		String keyPath = trimLeadingSlash(path);
		if (Boolean.TRUE.equals(config.getStyleAccessEnabled())) {
			if (!sameAuthority(endpoint, uri) || !keyPath.startsWith(config.getBucket() + "/")) {
				throw new IllegalStateException("对象地址不属于当前 path-style Bucket");
			}
			keyPath = keyPath.substring(config.getBucket().length() + 1);
		}
		else {
			String expectedHost = config.getBucket() + "." + endpoint.getHost();
			if (!expectedHost.equalsIgnoreCase(uri.getHost())) {
				throw new IllegalStateException("对象地址不属于当前 Bucket");
			}
		}
		return requireSafeObjectKey(keyPath);
	}

	private URI normalizeUri(String value) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalStateException("系统存储访问地址未配置");
		}
		String normalized = value.trim();
		if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
			normalized = "https://" + normalized;
		}
		URI uri = URI.create(normalized);
		if (uri.getHost() == null) {
			throw new IllegalStateException("系统存储访问地址格式无效");
		}
		return uri;
	}

	private boolean sameAuthority(URI left, URI right) {
		int leftPort = left.getPort() < 0 ? defaultPort(left.getScheme()) : left.getPort();
		int rightPort = right.getPort() < 0 ? defaultPort(right.getScheme()) : right.getPort();
		return left.getScheme().equalsIgnoreCase(right.getScheme()) && left.getHost().equalsIgnoreCase(right.getHost())
				&& leftPort == rightPort;
	}

	private int defaultPort(String scheme) {
		return "http".equalsIgnoreCase(scheme) ? 80 : 443;
	}

	private String trimLeadingSlash(String value) {
		String result = value;
		while (result.startsWith("/")) {
			result = result.substring(1);
		}
		return result;
	}

	private String requireSafeObjectKey(String value) {
		if (!StringUtils.hasText(value) || value.startsWith("/") || value.contains("\\") || value.contains("../")) {
			throw new IllegalStateException("存储对象键无效");
		}
		return value;
	}

	private SysStorageConfigDTO toDto(SysStorageConfig entity) {
		SysStorageConfigDTO dto = new SysStorageConfigDTO();
		dto.setId(entity.getId());
		dto.setType(entity.getType());
		dto.setAccessKey(entity.getAccessKey());
		dto.setAccessSecret(entity.getAccessSecret());
		dto.setEndpoint(entity.getEndpoint());
		dto.setBucket(entity.getBucket());
		dto.setDir(entity.getDir());
		dto.setDomain(entity.getDomain());
		dto.setStyleAccessEnabled("1".equals(entity.getStyleAccessEnabled()));
		return dto;
	}

}
