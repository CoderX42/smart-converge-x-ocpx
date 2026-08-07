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
		assertEquals(OcpxEventType.EVOKE, OcpxEventType.fromExternal("evoke"));
		assertEquals(OcpxEventType.LOGIN, OcpxEventType.fromExternal("login"));
		assertEquals(OcpxEventType.REGISTER, OcpxEventType.fromExternal("register"));
		assertEquals(OcpxEventType.NEXT_DAY_RETENTION, OcpxEventType.fromExternal("day1retention"));
		assertEquals(OcpxEventType.RETENTION, OcpxEventType.fromExternal("retention"));
		assertEquals(OcpxEventType.PAYMENT, OcpxEventType.fromExternal("purchase"));
		assertEquals(OcpxEventType.PAYMENT, OcpxEventType.fromExternal("payment"));
		assertEquals(OcpxEventType.REACTIVATION, OcpxEventType.fromExternal("reactivation"));
		assertEquals(OcpxEventType.CUSTOMER_ACQUISITION, OcpxEventType.fromExternal("new"));
		assertEquals(OcpxEventType.VISIT, OcpxEventType.fromExternal("visits"));
		assertEquals(OcpxEventType.AUTHENTICATION, OcpxEventType.fromExternal("authentication"));
		assertEquals(OcpxEventType.ORDER, OcpxEventType.fromExternal("order"));
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
		FeedbackReqV1 left = mapper.readValue("{\"event\":\"click\",\"monitor_id\":\"m1\",\"ext\":{\"b\":2,\"a\":1}}",
				FeedbackReqV1.class);
		FeedbackReqV1 right = mapper.readValue("{\"ext\":{\"a\":1,\"b\":2},\"monitor_id\":\"m1\",\"event\":\"click\"}",
				FeedbackReqV1.class);

		var writer = mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
		assertEquals(writer.writeValueAsString(left), writer.writeValueAsString(right));
	}

}
