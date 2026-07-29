package com.yzytech.scx.delivery.service;

import com.yzytech.scx.delivery.entity.IntegrationDocument;
import com.yzytech.scx.delivery.entity.UpstreamCustomer;
import com.yzytech.scx.delivery.mapper.IntegrationDocumentMapper;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.delivery.mapper.PartnerMapper;
import com.yzytech.scx.delivery.mapper.ProductMapper;
import com.yzytech.scx.delivery.mapper.UpstreamCustomerMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.service.StorageUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterDataServiceTest {

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final UpstreamCustomerMapper customerMapper = mock(UpstreamCustomerMapper.class);

	private final IntegrationDocumentMapper documentMapper = mock(IntegrationDocumentMapper.class);

	private final StorageUploadService storageUploadService = mock(StorageUploadService.class);

	private final MasterDataService service = new MasterDataService(scopeService, mock(PartnerMapper.class),
			customerMapper, mock(ProductMapper.class), documentMapper, mock(MediaChannelMapper.class),
			storageUploadService);

	@Test
	void externalUrlAllowsHttpAndHttpsWithoutFetchingContent() {
		assertThatCode(() -> service.validateExternalUrl("http://docs.example.com/guide")).doesNotThrowAnyException();
		assertThatCode(() -> service.validateExternalUrl("https://example.com/api/v1")).doesNotThrowAnyException();
	}

	@Test
	void externalUrlRejectsCredentialsAndNonHttpSchemes() {
		assertThatThrownBy(() -> service.validateExternalUrl("https://user:pass@example.com/callback"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不能携带用户名密码");
		assertThatThrownBy(() -> service.validateExternalUrl("file:///etc/passwd"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HTTP 或 HTTPS");
	}

	@Test
	void savesCustomerOnlyWithinWritableMediaScope() {
		UpstreamCustomer customer = new UpstreamCustomer();
		customer.setMediaId("media-1");
		customer.setCode("C-1");
		customer.setName("客户一");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(customerMapper.insert(customer)).thenReturn(1);

		service.saveCustomer(customer);

		assertThat(customer.getStatus()).isEqualTo("ACTIVE");
		verify(customerMapper).insert(customer);
	}

	@Test
	void uploadsAllowedDocumentOnlyWithinWritableMediaScope() {
		MockMultipartFile file = new MockMultipartFile("file", "integration-guide.pdf", "text/html",
				"guide".getBytes());
		ManagedStorageObject object = new ManagedStorageObject("storage-1", "oss", "documents/integration-guide.pdf",
				"https://oss.example/documents/integration-guide.pdf");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(storageUploadService.uploadManaged(any(), eq("integration-guide.pdf"), eq("application/pdf"), eq(5L)))
			.thenReturn(object);

		String url = service.uploadDocument("media-1", file);

		assertThat(url).isEqualTo(object.url());
		verify(storageUploadService).uploadManaged(any(), eq("integration-guide.pdf"), eq("application/pdf"), eq(5L));
	}

	@Test
	void rejectsUnsupportedEmptyAndOversizedDocumentUpload() {
		when(scopeService.canWrite("media-1")).thenReturn(true);

		assertThatThrownBy(() -> service.uploadDocument("media-1",
				new MockMultipartFile("file", "script.exe", "application/octet-stream", new byte[1])))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("仅支持 PDF、Office、文本、CSV、JSON 和 YAML 文档");
		assertThatThrownBy(() -> service.uploadDocument("media-1",
				new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("请选择需要上传的文档");
		assertThatThrownBy(() -> service.uploadDocument("media-1",
				new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[20 * 1024 * 1024 + 1])))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("文档大小不能超过 20MB");
	}

	@Test
	void createsManagedDocumentWithHashAndStorageMetadata() {
		MockMultipartFile file = new MockMultipartFile("file", "guide.md", "text/markdown", "# guide".getBytes());
		ManagedStorageObject object = new ManagedStorageObject("storage-1", "local", "managed/guide.md",
				"https://files.example/file/local/managed/guide.md");
		IntegrationDocument value = document("doc-1", 0, 1, "OBJECT");
		value.setId(null);
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(storageUploadService.uploadManaged(any(), eq("guide.md"), eq("text/markdown;charset=UTF-8"), eq(7L)))
			.thenReturn(object);
		when(documentMapper.insert(value)).thenReturn(1);

		IntegrationDocument saved = service.saveDocument(value, file);

		assertThat(saved.getStorageConfigId()).isEqualTo("storage-1");
		assertThat(saved.getStorageObjectKey()).isEqualTo("managed/guide.md");
		assertThat(saved.getOriginalFileName()).isEqualTo("guide.md");
		assertThat(saved.getFileSize()).isEqualTo(7L);
		assertThat(saved.getContentHash()).hasSize(64);
		assertThat(saved.getDocumentVersion()).isEqualTo(1);
	}

	@Test
	void metadataUpdateKeepsBusinessVersionAndManagedObject() {
		IntegrationDocument existing = document("doc-1", 2, 4, "OBJECT");
		existing.setObjectKey("https://files.example/file/local/a.pdf");
		existing.setOriginalFileName("a.pdf");
		existing.setFileSize(3L);
		IntegrationDocument update = document("doc-1", 2, null, "OBJECT");
		update.setName("更新后的名称");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(documentMapper.selectForUpdate("doc-1")).thenReturn(existing);
		when(documentMapper.updateById(update)).thenReturn(1);

		IntegrationDocument saved = service.saveDocument(update, null);

		assertThat(saved.getDocumentVersion()).isEqualTo(4);
		assertThat(saved.getObjectKey()).endsWith("/a.pdf");
		verify(storageUploadService, never()).delete(any());
		verify(storageUploadService, never()).resolve(any());
	}

	@Test
	void replacementIncrementsBusinessVersionAndDeletesOldObject() {
		IntegrationDocument existing = document("doc-1", 2, 4, "OBJECT");
		existing.setObjectKey("https://files.example/file/local/old.pdf");
		existing.setStorageConfigId("storage-1");
		existing.setStorageProvider("local");
		existing.setStorageObjectKey("old.pdf");
		IntegrationDocument update = document("doc-1", 2, null, "OBJECT");
		MockMultipartFile replacement = new MockMultipartFile("file", "new.pdf", "application/pdf", "new".getBytes());
		ManagedStorageObject newObject = new ManagedStorageObject("storage-1", "local", "new.pdf",
				"https://files.example/file/local/new.pdf");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(documentMapper.selectForUpdate("doc-1")).thenReturn(existing);
		when(storageUploadService.uploadManaged(any(), eq("new.pdf"), eq("application/pdf"), eq(3L)))
			.thenReturn(newObject);
		when(documentMapper.updateById(update)).thenReturn(1);

		IntegrationDocument saved = service.saveDocument(update, replacement);

		assertThat(saved.getDocumentVersion()).isEqualTo(5);
		verify(storageUploadService).delete(
				new ManagedStorageObject("storage-1", "local", "old.pdf", "https://files.example/file/local/old.pdf"));
	}

	@Test
	void replacementFailureCompensatesByDeletingNewObject() {
		IntegrationDocument existing = document("doc-1", 2, 4, "OBJECT");
		existing.setObjectKey("https://files.example/file/local/old.pdf");
		existing.setStorageConfigId("storage-1");
		existing.setStorageProvider("local");
		existing.setStorageObjectKey("old.pdf");
		IntegrationDocument update = document("doc-1", 2, null, "OBJECT");
		MockMultipartFile replacement = new MockMultipartFile("file", "new.pdf", "application/pdf", "new".getBytes());
		ManagedStorageObject oldObject = new ManagedStorageObject("storage-1", "local", "old.pdf",
				"https://files.example/file/local/old.pdf");
		ManagedStorageObject newObject = new ManagedStorageObject("storage-1", "local", "new.pdf",
				"https://files.example/file/local/new.pdf");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(documentMapper.selectForUpdate("doc-1")).thenReturn(existing);
		when(storageUploadService.uploadManaged(any(), eq("new.pdf"), eq("application/pdf"), eq(3L)))
			.thenReturn(newObject);
		when(documentMapper.updateById(update)).thenReturn(1);
		doThrow(new IllegalStateException("删除对象存储文件失败")).when(storageUploadService).delete(oldObject);

		assertThatThrownBy(() -> service.saveDocument(update, replacement)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("删除对象存储文件失败");
		verify(storageUploadService).delete(newObject);
	}

	@Test
	void rejectsOptimisticLockConflictAndHiddenCrossMediaAccess() {
		IntegrationDocument existing = document("doc-1", 3, 1, "EXTERNAL");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(documentMapper.selectForUpdate("doc-1")).thenReturn(existing);

		assertThatThrownBy(() -> service.saveDocument(document("doc-1", 2, 1, "EXTERNAL"), null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("已被其他用户修改");

		when(scopeService.canWrite("media-1")).thenReturn(false);
		assertThatThrownBy(() -> service.deleteDocument("doc-1", 3)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("资源不存在");
	}

	@Test
	void blocksPermanentDeleteWhileDocumentHasBusinessReferences() {
		IntegrationDocument existing = document("doc-1", 2, 4, "OBJECT");
		existing.setObjectKey("https://files.example/file/local/a.pdf");
		existing.setStorageConfigId("storage-1");
		existing.setStorageProvider("local");
		existing.setStorageObjectKey("a.pdf");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(documentMapper.selectForUpdate("doc-1")).thenReturn(existing);
		when(documentMapper.countReferences("doc-1")).thenReturn(2L);

		assertThatThrownBy(() -> service.deleteDocument("doc-1", 2)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("历史报表引用");
		verify(storageUploadService, never()).delete(any());
		verify(documentMapper, never()).deleteById("doc-1");
	}

	@Test
	void deletesManagedObjectBeforeMetadataWhenUnreferenced() {
		IntegrationDocument existing = document("doc-1", 2, 4, "OBJECT");
		existing.setObjectKey("https://files.example/file/local/a.pdf");
		existing.setStorageConfigId("storage-1");
		existing.setStorageProvider("local");
		existing.setStorageObjectKey("a.pdf");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(documentMapper.selectForUpdate("doc-1")).thenReturn(existing);
		when(documentMapper.countReferences("doc-1")).thenReturn(0L);
		when(documentMapper.deleteById("doc-1")).thenReturn(1);

		assertThat(service.deleteDocument("doc-1", 2)).isTrue();

		verify(storageUploadService)
			.delete(new ManagedStorageObject("storage-1", "local", "a.pdf", "https://files.example/file/local/a.pdf"));
		verify(documentMapper).deleteById("doc-1");
	}

	private IntegrationDocument document(String id, int version, Integer documentVersion, String storageType) {
		IntegrationDocument value = new IntegrationDocument();
		value.setId(id);
		value.setMediaId("media-1");
		value.setName("文档");
		value.setDescription("说明");
		value.setType("OTHER");
		value.setStatus("DRAFT");
		value.setStorageType(storageType);
		value.setVersion(version);
		value.setDocumentVersion(documentVersion);
		if ("EXTERNAL".equals(storageType))
			value.setExternalUrl("https://docs.example.com/guide");
		return value;
	}

}
