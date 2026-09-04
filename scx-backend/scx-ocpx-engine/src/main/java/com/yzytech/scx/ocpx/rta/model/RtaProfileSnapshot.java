package com.yzytech.scx.ocpx.rta.model;

/** Engine 读取的 Profile 快照，不承载密钥。 */
public record RtaProfileSnapshot(String id, String adapterCode, String configJson) {
}
