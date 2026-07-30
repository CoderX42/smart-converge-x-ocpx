package com.yzytech.scx.delivery.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.yzytech.scx.delivery.entity.MediaChannelUser;
import org.junit.jupiter.api.Test;

class MediaChannelUserViewTest {

	@Test
	void masksContactValuesInListView() {
		MediaChannelUser value = new MediaChannelUser();
		value.setPhone("13800138000");
		value.setEmail("operator@example.com");

		MediaChannelUserView view = MediaChannelUserView.masked(value);

		assertThat(view.phone()).isEqualTo("138****8000");
		assertThat(view.email()).isEqualTo("o***@example.com");
	}

	@Test
	void keepsContactValuesOnlyInSensitiveView() {
		MediaChannelUser value = new MediaChannelUser();
		value.setPhone("13800138000");
		value.setEmail("operator@example.com");

		MediaChannelUserView view = MediaChannelUserView.sensitive(value);

		assertThat(view.phone()).isEqualTo("13800138000");
		assertThat(view.email()).isEqualTo("operator@example.com");
	}
}
