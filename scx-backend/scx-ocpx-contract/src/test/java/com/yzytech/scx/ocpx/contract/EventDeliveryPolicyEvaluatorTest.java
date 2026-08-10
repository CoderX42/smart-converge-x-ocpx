package com.yzytech.scx.ocpx.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventDeliveryPolicyEvaluatorTest {

	@Test
	void replayUsesTheSameStableBucketAndDecision() {
		var policy = new EventDeliveryPolicyEvaluator.Policy(4321, null, false, "snapshot-fingerprint");
		var fact = new EventDeliveryPolicyEvaluator.Fact("event-id|route-version|definition-revision", false);

		var first = EventDeliveryPolicyEvaluator.evaluate(fact, policy, 0);
		var replay = EventDeliveryPolicyEvaluator.evaluate(fact, policy, 0);

		assertEquals(first, replay);
		assertTrue(first.originalFactPreserved());
	}

	@Test
	void duplicateNeverConsumesLimitOrCreatesDeliverySideEffect() {
		var decision = EventDeliveryPolicyEvaluator.evaluate(new EventDeliveryPolicyEvaluator.Fact("fact-1", true),
				new EventDeliveryPolicyEvaluator.Policy(10000, 10L, true, "policy-1"), 9);

		assertEquals("DUPLICATE", decision.reason());
		assertFalse(decision.consumeLimit());
		assertFalse(decision.deliver());
		assertTrue(decision.originalFactPreserved());
	}

	@Test
	void zeroLimitAndOverLimitSwitchDoNotRewriteOriginalFact() {
		var held = EventDeliveryPolicyEvaluator.evaluate(new EventDeliveryPolicyEvaluator.Fact("fact-2", false),
				new EventDeliveryPolicyEvaluator.Policy(10000, 0L, false, "policy-2"), 0);
		var delivered = EventDeliveryPolicyEvaluator.evaluate(new EventDeliveryPolicyEvaluator.Fact("fact-2", false),
				new EventDeliveryPolicyEvaluator.Policy(10000, 0L, true, "policy-2"), 0);

		assertEquals("OVER_LIMIT_HELD", held.reason());
		assertFalse(held.deliver());
		assertEquals("OVER_LIMIT_DELIVERED", delivered.reason());
		assertTrue(delivered.deliver());
		assertFalse(delivered.consumeLimit());
		assertTrue(held.originalFactPreserved());
		assertTrue(delivered.originalFactPreserved());
	}

	@Test
	void rejectsInvalidNumericContract() {
		assertThrows(IllegalArgumentException.class,
				() -> new EventDeliveryPolicyEvaluator.Policy(10001, null, false, "policy"));
		assertThrows(IllegalArgumentException.class,
				() -> new EventDeliveryPolicyEvaluator.Policy(0, -1L, false, "policy"));
	}

}
