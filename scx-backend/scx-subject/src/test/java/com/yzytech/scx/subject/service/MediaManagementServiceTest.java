package com.yzytech.scx.subject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yzytech.scx.subject.dto.MediaCreateRequest;
import com.yzytech.scx.subject.dto.MediaStatusRequest;
import com.yzytech.scx.subject.dto.MediaUpdateRequest;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.mapper.MediaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class MediaManagementServiceTest {

	@Mock
	private MediaMapper mediaMapper;

	@Test
	void createsActiveCnyMediaWithValidatedTimezone() {
		MediaManagementService service = new MediaManagementService(mediaMapper);
		when(mediaMapper.insert(any(Media.class))).thenAnswer(invocation -> {
			Media value = invocation.getArgument(0);
			value.setId("media-1");
			return 1;
		});

		MediaCreateRequest request = createRequest();
		Media created = service.create(request);

		assertThat(created.getCode()).isEqualTo("uat-media");
		assertThat(created.getStatus()).isEqualTo("ACTIVE");
		assertThat(created.getSettlementCurrency()).isEqualTo("CNY");
		assertThat(created.getVersion()).isZero();
	}

	@Test
	void rejectsInvalidTimezoneBeforeInsert() {
		MediaManagementService service = new MediaManagementService(mediaMapper);
		MediaCreateRequest request = createRequest();
		request.setTimezone("Mars/Olympus");

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("无效时区");
	}

	@Test
	void rejectsFixedOffsetInsteadOfIanaTimezone() {
		MediaManagementService service = new MediaManagementService(mediaMapper);
		MediaCreateRequest request = createRequest();
		request.setTimezone("+08:00");

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("无效时区");
	}

	@Test
	void rejectsNonCnySettlementCurrency() {
		MediaManagementService service = new MediaManagementService(mediaMapper);
		MediaCreateRequest request = createRequest();
		request.setSettlementCurrency("USD");

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("仅支持 CNY 结算");
	}

	@Test
	void mapsUniqueCodeConflictToBusinessError() {
		MediaManagementService service = new MediaManagementService(mediaMapper);
		when(mediaMapper.insert(any(Media.class))).thenThrow(new DuplicateKeyException("duplicate"));

		assertThatThrownBy(() -> service.create(createRequest())).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("媒体编码已存在");
	}

	@Test
	void rejectsStaleStatusVersion() {
		MediaManagementService service = new MediaManagementService(mediaMapper);
		when(mediaMapper.selectById("media-1")).thenReturn(existing());
		when(mediaMapper.updateById(any(Media.class))).thenReturn(0);
		MediaStatusRequest request = new MediaStatusRequest();
		request.setStatus("DISABLED");
		request.setVersion(0);

		assertThatThrownBy(() -> service.changeStatus("media-1", request)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("刷新后重试");
	}

	@Test
	void codeIsNotPartOfUpdateContract() {
		MediaManagementService service = new MediaManagementService(mediaMapper);
		Media before = existing();
		Media after = existing();
		after.setName("更新名称");
		after.setVersion(1);
		when(mediaMapper.selectById("media-1")).thenReturn(before, after);
		when(mediaMapper.updateById(any(Media.class))).thenAnswer(invocation -> {
			Media update = invocation.getArgument(0);
			assertThat(update.getCode()).isNull();
			return 1;
		});
		MediaUpdateRequest request = new MediaUpdateRequest();
		request.setName("更新名称");
		request.setTimezone("Asia/Shanghai");
		request.setSettlementCurrency("CNY");
		request.setVersion(0);

		assertThat(service.update("media-1", request).getName()).isEqualTo("更新名称");
	}

	private MediaCreateRequest createRequest() {
		MediaCreateRequest request = new MediaCreateRequest();
		request.setCode("uat-media");
		request.setName("UAT 媒体");
		request.setTimezone("Asia/Shanghai");
		request.setSettlementCurrency("CNY");
		return request;
	}

	private Media existing() {
		Media media = new Media();
		media.setId("media-1");
		media.setCode("immutable-code");
		media.setName("媒体");
		media.setStatus("ACTIVE");
		media.setTimezone("Asia/Shanghai");
		media.setSettlementCurrency("CNY");
		media.setVersion(0);
		return media;
	}

}
