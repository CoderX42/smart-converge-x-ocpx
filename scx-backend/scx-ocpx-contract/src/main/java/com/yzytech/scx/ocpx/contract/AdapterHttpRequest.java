package com.yzytech.scx.ocpx.contract;

import java.net.URI;
import java.util.Map;

public record AdapterHttpRequest(String method, URI uri, Map<String, String> headers, String body) {
}
