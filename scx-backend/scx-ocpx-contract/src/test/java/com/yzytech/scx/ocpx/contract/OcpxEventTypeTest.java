package com.yzytech.scx.ocpx.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

class OcpxEventTypeTest {

	@Test
	void mapsAllCocoCallbackActions() {
		assertEquals(OcpxEventType.ACTIVATE, OcpxEventType.fromExternal("activate"));
		assertEquals(OcpxEventType.FIRST_ACTIVATION, OcpxEventType.fromExternal("first_activation"));
		assertEquals(OcpxEventType.EVOKE, OcpxEventType.fromExternal("evoke"));
		assertEquals(OcpxEventType.LOGIN, OcpxEventType.fromExternal("login"));
		assertEquals(OcpxEventType.REGISTER, OcpxEventType.fromExternal("register"));
		assertEquals(OcpxEventType.NEXT_DAY_RETENTION, OcpxEventType.fromExternal("day1retention"));
		assertEquals(OcpxEventType.RETENTION_D3, OcpxEventType.fromExternal("retention_d3"));
		assertEquals(OcpxEventType.RETENTION_D4, OcpxEventType.fromExternal("day4retention"));
		assertEquals(OcpxEventType.RETENTION_D5, OcpxEventType.fromExternal("retention5"));
		assertEquals(OcpxEventType.RETENTION_D6, OcpxEventType.fromExternal("retention_d6"));
		assertEquals(OcpxEventType.RETENTION_D7, OcpxEventType.fromExternal("day7retention"));
		assertEquals(OcpxEventType.RETENTION, OcpxEventType.fromExternal("retention"));
		assertEquals(OcpxEventType.PAYMENT, OcpxEventType.fromExternal("purchase"));
		assertEquals(OcpxEventType.PAYMENT, OcpxEventType.fromExternal("payment"));
		assertEquals(OcpxEventType.REACTIVATION, OcpxEventType.fromExternal("reactivation"));
		assertEquals(OcpxEventType.CUSTOMER_ACQUISITION, OcpxEventType.fromExternal("new"));
		assertEquals(OcpxEventType.VISIT, OcpxEventType.fromExternal("visits"));
		assertEquals(OcpxEventType.AUTHENTICATION, OcpxEventType.fromExternal("authentication"));
		assertEquals(OcpxEventType.ORDER, OcpxEventType.fromExternal("order"));
		assertEquals(OcpxEventType.FIRST_ORDER, OcpxEventType.fromExternal("firstorder"));
		assertEquals(OcpxEventType.FIRST_ORDER, OcpxEventType.fromExternal("first_order"));
		assertEquals(OcpxEventType.FIRST_ORDER, OcpxEventType.fromExternal("first_purchase"));
		assertEquals(OcpxEventType.FIRST_REACTIVATION, OcpxEventType.fromExternal("firstreactivation"));
		assertEquals(OcpxEventType.COMPLETE_ORDER, OcpxEventType.fromExternal("completeorder"));
	}

	@Test
	void rejectsUnknownActions() {
		assertThrows(IllegalArgumentException.class, () -> OcpxEventType.fromExternal("unknown"));
	}

	@Test
	void normalizesFeedbackPropertyOrderForIdempotency() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		FeedbackReqV2 left = mapper.readValue("""
				{"event":"click","monitor_task_id":"m1","event_id":"e1","event_time":1,
				 "device":{"b":2,"a":1}}
				""", FeedbackReqV2.class);
		FeedbackReqV2 right = mapper.readValue("""
				{"device":{"a":1,"b":2},"event_time":1,"event_id":"e1",
				 "monitor_task_id":"m1","event":"click"}
				""", FeedbackReqV2.class);

		var writer = mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
		assertEquals(writer.writeValueAsString(left), writer.writeValueAsString(right));
	}

}
