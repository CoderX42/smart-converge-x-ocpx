package com.yzytech.scx.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.delivery.entity.IntegrationDocument;
import com.yzytech.scx.delivery.entity.UpstreamCustomer;
import com.yzytech.scx.delivery.mapper.IntegrationDocumentMapper;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.delivery.mapper.PartnerMapper;
import com.yzytech.scx.delivery.mapper.ProductMapper;
import com.yzytech.scx.delivery.mapper.UpstreamCustomerMapper;
import com.yzytech.scx.delivery.service.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

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

	@Test
	void hidesUnexpectedDocumentUploadFailureFromApiResponse() {
		MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[] { 1 });
		when(service.uploadDocument("media-1", file)).thenThrow(new RuntimeException("internal detail"));

		var result = controller.uploadDocument("media-1", file);

		assertThat(result.getCode()).isEqualTo(1);
		assertThat(result.getMsg()).isEqualTo("文件上传失败");
		assertThat(result.getData()).isNull();
	}

	@Test
	void contentResponseUsesPrivateInlineHeadersAndUtf8FileName() throws Exception {
		IntegrationDocument document = new IntegrationDocument();
		document.setId("doc-1");
		document.setName("媒体协议");
		document.setOriginalFileName("媒体回传协议.pdf");
		document.setMimeType("application/pdf");
		document.setFileSize(3L);
		when(service.openDocument("doc-1"))
			.thenReturn(new MasterDataService.DocumentContent(document, new ByteArrayInputStream("PDF".getBytes())));

		var response = controller.documentContent("doc-1");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		response.getBody().writeTo(output);

		assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;")
			.contains("UTF-8");
		assertThat(response.getHeaders().getCacheControl()).contains("no-store").contains("private");
		assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(output.toString()).isEqualTo("PDF");
	}

	@Test
	void documentEndpointsUseDedicatedReadWriteAndDeletePermissions() throws Exception {
		assertPermission("documents", "ad:integration-document:read", Page.class, String.class, String.class,
				String.class, String.class, String.class);
		assertPermission("saveDocument", "ad:integration-document:write", IntegrationDocument.class);
		assertPermission("updateDocument", "ad:integration-document:write", String.class, IntegrationDocument.class);
		assertPermission("documentContent", "ad:integration-document:read", String.class);
		assertPermission("deleteDocument", "ad:integration-document:delete", String.class, Integer.class);
	}

	@Test
	void partnerEndpointsAcceptDedicatedAndLegacyPermissions() throws Exception {
		assertAnyPermission("partners", new String[] { "ad:partner:read", "ad:master:read" }, Page.class, String.class,
				String.class, String.class);
		assertAnyPermission("savePartner", new String[] { "ad:partner:write", "ad:master:write" },
				com.yzytech.scx.delivery.entity.Partner.class);
		assertAnyPermission("updatePartner", new String[] { "ad:partner:write", "ad:master:write" }, String.class,
				com.yzytech.scx.delivery.entity.Partner.class);
	}

	private void assertPermission(String methodName, String permission, Class<?>... parameters) throws Exception {
		SaCheckPermission annotation = MasterDataController.class.getDeclaredMethod(methodName, parameters)
			.getAnnotation(SaCheckPermission.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).containsExactly(permission);
	}

	private void assertAnyPermission(String methodName, String[] permissions, Class<?>... parameters) throws Exception {
		SaCheckPermission annotation = MasterDataController.class.getDeclaredMethod(methodName, parameters)
			.getAnnotation(SaCheckPermission.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).containsExactly(permissions);
		assertThat(annotation.mode()).isEqualTo(SaMode.OR);
	}

}
