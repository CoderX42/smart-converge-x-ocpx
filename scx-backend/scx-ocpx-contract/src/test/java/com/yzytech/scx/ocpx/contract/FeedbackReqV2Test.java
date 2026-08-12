package com.yzytech.scx.ocpx.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FeedbackReqV2Test {

	@Test
	void derivesBusinessRelationFromMonitorTaskInsteadOfDuplicateIds() throws Exception {
		FeedbackReqV2 request = new ObjectMapper().readValue("""
				{"schema_version":"2.0","monitor_task_id":"task-1","event":"CLICK","event_id":"evt-1",
				 "event_time":1710000000000,
				 "extensions":{"advertiser_id":"untrusted","media_id":"untrusted"}}
				""", FeedbackReqV2.class);

		assertEquals("task-1", request.getMonitorTaskId());
		assertEquals("untrusted", request.extensions().get("advertiser_id"));
		assertEquals("untrusted", request.extensions().get("media_id"));
		assertNull(request.extensions().get("promotion_link_id"));
	}

}
