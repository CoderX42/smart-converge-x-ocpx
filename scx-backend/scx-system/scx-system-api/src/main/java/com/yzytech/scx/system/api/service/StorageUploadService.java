package com.yzytech.scx.system.api.service;

import java.io.InputStream;

/**
 * Uploads a file through the currently active system storage configuration.
 *
 * <p>Callers receive only the stored object's address. Storage credentials remain
 * inside the system storage module.</p>
 */
public interface StorageUploadService {

	String upload(InputStream inputStream, String fileName, String contentType, long size);

}
