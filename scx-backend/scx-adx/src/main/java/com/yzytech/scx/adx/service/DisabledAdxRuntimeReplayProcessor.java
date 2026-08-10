package com.yzytech.scx.adx.service;

import org.springframework.stereotype.Component;

/** No external processor is wired in S10 source or local-test profiles. */
@Component
public class DisabledAdxRuntimeReplayProcessor implements AdxRuntimeReplayProcessor {

	@Override
	public boolean available() {
		return false;
	}

	@Override
	public Result process(Context context) {
		throw new IllegalStateException("S10 replay processor is not configured");
	}

}
