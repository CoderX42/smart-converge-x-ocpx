package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.entity.ChannelClient;

public record ChannelAuthContext(ChannelClient client, String keyVersion, String sharedSecret) {
}
