package com.yzytech.scx.ocpx.rta.admission;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import com.yzytech.scx.ocpx.rta.exception.RtaAdmissionDeniedException;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



class RtaAdmissionStoreTest {

	@Test
	void savesWithConfiguredTtlAndAllowsMatchingClick() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		RtaAdmissionStore store = new RtaAdmissionStore(redis);
		store.save(admission(Instant.now().plusSeconds(300).getEpochSecond()), 300);
		verify(values).set(anyString(), anyString(), anyLong(), any());

		when(values.get(anyString())).thenReturn(com.alibaba.fastjson2.JSON.toJSONString(
				admission(Instant.now().plusSeconds(300).getEpochSecond())));
		store.requireAllowed(click());
	}

	@Test
	void rejectsMissingExpiredAndMismatchedAdmissions() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		RtaAdmissionStore store = new RtaAdmissionStore(redis);
		when(values.get(anyString())).thenReturn(null);
		assertThatThrownBy(() -> store.requireAllowed(click())).isInstanceOf(RtaAdmissionDeniedException.class)
				.hasMessage("RTA_ADMISSION_MISSING");
		when(values.get(anyString())).thenReturn(com.alibaba.fastjson2.JSON.toJSONString(admission(1)));
		assertThatThrownBy(() -> store.requireAllowed(click())).isInstanceOf(RtaAdmissionDeniedException.class)
				.hasMessage("RTA_ADMISSION_EXPIRED");
		when(values.get(anyString())).thenReturn(com.alibaba.fastjson2.JSON.toJSONString(
				new RtaAdmissionStore.Admission("req-1", "hash", "other", "link-1", "media-1", "task-1", "profile-1",
						RtaDecision.ALLOW, "MOCK_ALLOW", List.of("ext-1"), Instant.now().plusSeconds(300).getEpochSecond())));
		assertThatThrownBy(() -> store.requireAllowed(click())).isInstanceOf(RtaAdmissionDeniedException.class)
				.hasMessage("RTA_ADMISSION_MISMATCH");
	}

	private RtaAdmissionStore.Admission admission(long expiresAt) {
		return new RtaAdmissionStore.Admission("req-1", "hash", "adv-1", "link-1", "media-1", "task-1", "profile-1",
				RtaDecision.ALLOW, "MOCK_ALLOW", List.of("ext-1"), expiresAt);
	}

	private FeedbackReq click() {
		return FeedbackReq.builder().mId("media-1").tId("task-1").aId("adv-1").lId("link-1")
				.reqId("req-1").event(EventTypeEnum.CLICK).build();
	}

}
