package com.yzytech.scx.ocpx.admin.service;

import java.net.URI;

public interface OcpxMonitorTaskTestHttpClient {

	Response get(URI uri);

	record Response(Integer statusCode, String body, String errorCode, long durationMs) {

	}

}
