package com.yzytech.scx.system.api.dto;

import java.io.Serializable;

/**
 * Reference to an object managed by the configured system storage.
 *
 * <p>
 * The reference deliberately contains no storage credentials. Business modules persist it
 * so later preview and deletion continue to use the storage configuration that originally
 * created the object.
 * </p>
 */
public record ManagedStorageObject(String storageConfigId, String provider, String objectKey,
		String url) implements Serializable {
}
