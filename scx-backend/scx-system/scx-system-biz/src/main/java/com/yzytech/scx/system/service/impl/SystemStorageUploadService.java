package com.yzytech.scx.system.service.impl;

import com.yzytech.scx.common.storage.handler.BootUploadFileHandler;
import com.yzytech.scx.common.storage.handler.StorageFactory;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import com.yzytech.scx.system.api.service.StorageUploadService;
import com.yzytech.scx.system.service.ISysStorageConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class SystemStorageUploadService implements StorageUploadService {

	private final ISysStorageConfigService storageConfigService;
	private final StorageFactory storageFactory;

	@Override
	public String upload(InputStream inputStream, String fileName, String contentType, long size) {
		SysStorageConfigDTO config = storageConfigService.getConfig();
		if (config == null || !StringUtils.hasText(config.getType())) {
			throw new IllegalStateException("文件存储配置为空");
		}
		BootUploadFileHandler handler = storageFactory.getStrategy(config.getType());
		if (handler == null) {
			throw new IllegalStateException("当前文件存储类型不可用");
		}
		return handler.uploadFile(config, inputStream, fileName, contentType, size);
	}

}
