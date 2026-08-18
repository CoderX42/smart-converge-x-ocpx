package com.yzytech.scx.ocpx.admin.service;

import java.net.URI;

/** 管理端媒体联调 Callback 的受限 HTTP 客户端。 */
public interface OcpxIntegrationCallbackHttpClient {

	Response get(URI uri);

	record Response(Integer statusCode, String body, String errorCode, long durationMs) {

	}

}
