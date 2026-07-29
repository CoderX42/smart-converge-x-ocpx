package com.yzytech.scx.system.api.service;

import com.yzytech.scx.system.api.dto.ManagedStorageObject;

import java.io.InputStream;

/**
 * Uploads a file through the currently active system storage configuration.
 *
 * <p>
 * Callers receive only the stored object's address. Storage credentials remain inside the
 * system storage module.
 * </p>
 */
public interface StorageUploadService {

	default String upload(InputStream inputStream, String fileName, String contentType, long size) {
		return uploadManaged(inputStream, fileName, contentType, size).url();
	}

	ManagedStorageObject uploadManaged(InputStream inputStream, String fileName, String contentType, long size);

	ManagedStorageObject resolve(String objectUrl);

	InputStream open(ManagedStorageObject object);

	void delete(ManagedStorageObject object);

}
