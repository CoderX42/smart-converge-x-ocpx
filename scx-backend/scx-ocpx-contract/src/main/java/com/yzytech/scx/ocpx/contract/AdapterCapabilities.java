package com.yzytech.scx.ocpx.contract;

import java.time.Duration;
import java.util.Set;

public record AdapterCapabilities(String direction, Set<OcpxEventType> events, Set<String> methods,
		Set<String> authTypes, Duration timeout, Set<String> macros, boolean callbackSupported) {
}
