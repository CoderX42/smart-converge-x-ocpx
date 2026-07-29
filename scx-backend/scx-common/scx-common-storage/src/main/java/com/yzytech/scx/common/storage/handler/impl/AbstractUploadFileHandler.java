
package com.yzytech.scx.common.storage.handler.impl;

import com.yzytech.scx.common.storage.entity.StorageConfig;
import com.yzytech.scx.common.storage.handler.BootUploadFileHandler;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;

import java.io.InputStream;

/**
 */
public abstract class AbstractUploadFileHandler implements BootUploadFileHandler {

	@Override
	public String uploadFile(SysStorageConfigDTO sysStorageConfig, InputStream inputStream, String fileName,
			String contextType, long size) {
		StorageConfig storageConfig = toStorageConfig(sysStorageConfig, contextType);
		return doUploadFile(storageConfig, inputStream, fileName, size);
	}

	public abstract String doUploadFile(StorageConfig storageConfig, InputStream inputStream, String fileName,
			long size);

	protected StorageConfig toStorageConfig(SysStorageConfigDTO sysStorageConfig, String contextType) {
		StorageConfig storageConfig = new StorageConfig();
		storageConfig.setAccessKeyId(sysStorageConfig.getAccessKey());
		storageConfig.setAccessKeySecret(sysStorageConfig.getAccessSecret());
		storageConfig.setBucket(sysStorageConfig.getBucket());
		storageConfig.setEndpoint(sysStorageConfig.getEndpoint());
		storageConfig.setDir(sysStorageConfig.getDir());
		storageConfig.setContextType(contextType);
		storageConfig.setDomain(sysStorageConfig.getDomain());
		storageConfig.setStyleAccessEnabled(sysStorageConfig.getStyleAccessEnabled());
		return storageConfig;
	}

}
