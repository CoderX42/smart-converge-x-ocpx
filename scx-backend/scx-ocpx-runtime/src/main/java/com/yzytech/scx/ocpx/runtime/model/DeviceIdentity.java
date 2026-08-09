package com.yzytech.scx.ocpx.runtime.model;

public record DeviceIdentity(String platform, String idType, int keyVersion, String keyHash) {
}
