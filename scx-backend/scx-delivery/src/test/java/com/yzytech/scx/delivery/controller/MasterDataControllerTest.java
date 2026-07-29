package com.yzytech.scx.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.delivery.entity.UpstreamCustomer;
import com.yzytech.scx.delivery.mapper.IntegrationDocumentMapper;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.delivery.mapper.PartnerMapper;
import com.yzytech.scx.delivery.mapper.ProductMapper;
import com.yzytech.scx.delivery.mapper.UpstreamCustomerMapper;
import com.yzytech.scx.delivery.service.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MasterDataControllerTest {

	private final UpstreamCustomerMapper customerMapper = mock(UpstreamCustomerMapper.class);

	private final MasterDataService service = mock(MasterDataService.class);

	private MasterDataController controller;

	@BeforeEach
	void setUp() {
		controller = new MasterDataController(mock(PartnerMapper.class), customerMapper, mock(ProductMapper.class),
				mock(MediaChannelMapper.class), mock(IntegrationDocumentMapper.class), service);
	}

	@Test
	void returnsAnEmptySuccessfulPageForReadableMedia() {
		when(service.readable("media-1")).thenReturn(true);
		when(customerMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

		var result = controller.customers(new Page<UpstreamCustomer>(1, 10), "media-1", null, null);

		assertThat(result.getCode()).isZero();
		assertThat(result.getData().getRecords()).isEmpty();
		verify(customerMapper).selectPage(any(), any());
	}

	@Test
	void reservesBusiness404ForMissingOrUnreadableMediaScope() {
		when(service.readable("media-other")).thenReturn(false);

		var result = controller.customers(new Page<UpstreamCustomer>(1, 10), "media-other", null, null);

		assertThat(result.getCode()).isEqualTo(404);
		assertThat(result.getData()).isNull();
		verify(customerMapper, never()).selectPage(any(), any());
	}

	@Test
	void returnsSafeStorageFailureMessageForDocumentUpload() {
		MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[] { 1 });
		when(service.uploadDocument("media-1", file)).thenThrow(new IllegalStateException("对象存储拒绝上传，请检查 Bucket 写入权限"));

		var result = controller.uploadDocument("media-1", file);

		assertThat(result.getCode()).isEqualTo(1);
		assertThat(result.getMsg()).isEqualTo("对象存储拒绝上传，请检查 Bucket 写入权限");
		assertThat(result.getData()).isNull();
	}

}
