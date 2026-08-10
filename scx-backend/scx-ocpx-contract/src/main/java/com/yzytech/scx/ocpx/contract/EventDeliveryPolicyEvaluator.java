package com.yzytech.scx.ocpx.contract;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Pure S6 evaluator: no Redis, database, Callback, billing, or forwarding side effects.
 */
public final class EventDeliveryPolicyEvaluator {

	private EventDeliveryPolicyEvaluator() {
	}

	public static Decision evaluate(Fact fact, Policy policy, long consumedBefore) {
		if (fact.duplicate()) {
			return new Decision("DUPLICATE", bucket(fact.stableFactKey(), policy.policyFingerprint()), false, false,
					false, true);
		}
		int bucket = bucket(fact.stableFactKey(), policy.policyFingerprint());
		boolean sampled = bucket < policy.callbackRatioBasisPoints();
		if (!sampled) {
			return new Decision("RATIO_FILTERED", bucket, false, false, false, true);
		}
		boolean overLimit = policy.limitValue() != null && consumedBefore >= policy.limitValue();
		if (overLimit) {
			return new Decision(policy.overLimitDelivery() ? "OVER_LIMIT_DELIVERED" : "OVER_LIMIT_HELD", bucket, true,
					false, policy.overLimitDelivery(), true);
		}
		return new Decision("DELIVER", bucket, true, true, true, true);
	}

	public static int bucket(String stableFactKey, String policyFingerprint) {
		if (stableFactKey == null || stableFactKey.isBlank() || policyFingerprint == null
				|| policyFingerprint.isBlank()) {
			throw new IllegalArgumentException("stable fact key and policy fingerprint are required");
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(("SHA256_MOD_10000_V1|" + policyFingerprint + "|" + stableFactKey)
					.getBytes(StandardCharsets.UTF_8));
			return new BigInteger(1, digest).mod(BigInteger.valueOf(10000)).intValue();
		}
		catch (Exception error) {
			throw new IllegalStateException("cannot evaluate deterministic event ratio", error);
		}
	}

	public record Fact(String stableFactKey, boolean duplicate) {
	}

	public record Policy(int callbackRatioBasisPoints, Long limitValue, boolean overLimitDelivery,
			String policyFingerprint) {

		public Policy {
			if (callbackRatioBasisPoints < 0 || callbackRatioBasisPoints > 10000) {
				throw new IllegalArgumentException("callbackRatioBasisPoints must be between 0 and 10000");
			}
			if (limitValue != null && limitValue < 0) {
				throw new IllegalArgumentException("limitValue must be non-negative or null");
			}
			if (policyFingerprint == null || policyFingerprint.isBlank()) {
				throw new IllegalArgumentException("policyFingerprint is required");
			}
		}
	}

	public record Decision(String reason, int deterministicBucket, boolean sampled, boolean consumeLimit,
			boolean deliver, boolean originalFactPreserved) {
	}

}
