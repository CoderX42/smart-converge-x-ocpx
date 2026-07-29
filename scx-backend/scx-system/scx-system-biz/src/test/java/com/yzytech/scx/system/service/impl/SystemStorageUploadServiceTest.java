package com.yzytech.scx.system.service.impl;

import com.yzytech.scx.common.storage.handler.BootUploadFileHandler;
import com.yzytech.scx.common.storage.handler.StorageFactory;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import com.yzytech.scx.system.api.entity.SysStorageConfig;
import com.yzytech.scx.system.service.ISysStorageConfigService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemStorageUploadServiceTest {

	private final ISysStorageConfigService configService = mock(ISysStorageConfigService.class);

	private final StorageFactory storageFactory = mock(StorageFactory.class);

	private final BootUploadFileHandler localHandler = mock(BootUploadFileHandler.class);

	private final SystemStorageUploadService service = new SystemStorageUploadService(configService, storageFactory);

	@Test
	void managedUploadReturnsOriginalConfigurationAndRealObjectKey() {
		SysStorageConfigDTO config = new SysStorageConfigDTO();
		config.setId("local-1");
		config.setType("local");
		config.setBucket("/data/files");
		when(configService.getConfig()).thenReturn(config);
		when(storageFactory.getStrategy("local")).thenReturn(localHandler);
		when(localHandler.uploadFile(eq(config), any(), eq("guide.pdf"), eq("application/pdf"), eq(3L)))
			.thenReturn("https://generated.example/boot/file/local/guide.pdf");

		ManagedStorageObject object = service.uploadManaged(new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
				"guide.pdf", "application/pdf", 3);

		assertThat(object).isEqualTo(new ManagedStorageObject("local-1", "local", "guide.pdf",
				"https://generated.example/boot/file/local/guide.pdf"));
	}

	@Test
	void resolvesHistoricalLocalObjectOnlyWhenConfiguredAuthorityMatches() {
		SysStorageConfig entity = localConfig("local-1", "https://files.example/boot");
		when(configService.list()).thenReturn(List.of(entity));

		ManagedStorageObject object = service.resolve("https://files.example/boot/file/local/archive/guide.pdf");

		assertThat(object.objectKey()).isEqualTo("archive/guide.pdf");
		assertThatThrownBy(() -> service.resolve("https://attacker.example/file/local/archive/guide.pdf"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("无法安全访问或删除");
	}

	@Test
	void deleteUsesTheStorageConfigurationPersistedWithDocument() {
		SysStorageConfig entity = localConfig("historical-local", "https://files.example");
		when(configService.getById("historical-local")).thenReturn(entity);
		when(storageFactory.getStrategy("local")).thenReturn(localHandler);
		ManagedStorageObject object = new ManagedStorageObject("historical-local", "local", "archive/guide.pdf",
				"https://files.example/file/local/archive/guide.pdf");

		service.delete(object);

		verify(localHandler).deleteFile(any(SysStorageConfigDTO.class), eq("archive/guide.pdf"));
	}

	private SysStorageConfig localConfig(String id, String domain) {
		SysStorageConfig entity = new SysStorageConfig();
		entity.setId(id);
		entity.setType("local");
		entity.setBucket("/data/files");
		entity.setDomain(domain);
		entity.setStatus("0");
		entity.setDelFlag("0");
		return entity;
	}

}
