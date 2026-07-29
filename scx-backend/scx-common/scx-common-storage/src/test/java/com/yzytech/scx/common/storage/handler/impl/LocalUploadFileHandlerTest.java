package com.yzytech.scx.common.storage.handler.impl;

import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalUploadFileHandlerTest {

	@TempDir
	Path storageRoot;

	private final LocalUploadFileHandler handler = new LocalUploadFileHandler();

	@Test
	void opensAndIdempotentlyDeletesFileInsideConfiguredRoot() throws Exception {
		Path file = storageRoot.resolve("documents/guide.txt");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "SCX");
		SysStorageConfigDTO config = config();

		try (var stream = handler.openFile(config, "documents/guide.txt")) {
			assertThat(new String(stream.readAllBytes())).isEqualTo("SCX");
		}
		handler.deleteFile(config, "documents/guide.txt");
		handler.deleteFile(config, "documents/guide.txt");

		assertThat(file).doesNotExist();
	}

	@Test
	void rejectsTraversalAbsolutePathsAndSymlinks() throws Exception {
		SysStorageConfigDTO config = config();
		assertThatThrownBy(() -> handler.openFile(config, "../outside.txt")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("对象键无效");
		assertThatThrownBy(() -> handler.openFile(config, "/etc/passwd")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("对象键无效");

		Path outside = Files.createTempFile("scx-storage-outside", ".txt");
		Path link = storageRoot.resolve("outside-link.txt");
		try {
			Files.createSymbolicLink(link, outside);
			assertThatThrownBy(() -> handler.openFile(config, "outside-link.txt"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("符号链接");
		}
		finally {
			Files.deleteIfExists(link);
			Files.deleteIfExists(outside);
		}
	}

	private SysStorageConfigDTO config() {
		SysStorageConfigDTO config = new SysStorageConfigDTO();
		config.setBucket(storageRoot.toString());
		return config;
	}

}
