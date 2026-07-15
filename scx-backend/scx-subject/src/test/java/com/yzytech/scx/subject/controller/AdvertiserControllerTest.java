package com.yzytech.scx.subject.controller;

import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.subject.entity.Advertiser;
import com.yzytech.scx.subject.service.AdvertiserReadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvertiserControllerTest {

	@Test
	void detailReturnsNotFoundWithoutDisclosingWhetherSubjectExists() {
		AdvertiserReadService service = mock(AdvertiserReadService.class);
		when(service.getForCurrentUser("adv-a")).thenReturn(Optional.empty());
		AdvertiserController controller = new AdvertiserController(service);

		ResponseEntity<Result<Advertiser>> response = controller.get("adv-a");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
		assertThat(response.getBody().getMsg()).isEqualTo("广告主体不存在或无访问权限");
	}
}
