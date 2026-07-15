package com.yzytech.scx.subject.controller;

import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.service.MediaReadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaControllerTest {

	@Test
	void detailReturnsNotFoundWithoutDisclosingWhetherSubjectExists() {
		MediaReadService service = mock(MediaReadService.class);
		when(service.getForCurrentUser("media-a")).thenReturn(Optional.empty());
		MediaController controller = new MediaController(service);

		ResponseEntity<Result<Media>> response = controller.get("media-a");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
		assertThat(response.getBody().getMsg()).isEqualTo("媒体主体不存在或无访问权限");
	}
}
