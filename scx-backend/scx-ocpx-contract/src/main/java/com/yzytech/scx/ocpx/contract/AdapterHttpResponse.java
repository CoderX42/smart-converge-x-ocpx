package com.yzytech.scx.ocpx.contract;

import java.util.List;
import java.util.Map;

public record AdapterHttpResponse(int statusCode, Map<String, List<String>> headers, String body) {
}
