package com.yzytech.scx.common.storage.handler.impl;

import com.yzytech.scx.common.storage.entity.StorageConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3UploadFileHandlerTest {

	@Test
	void usesAliyunNativeUploadForAliyunEndpoint() {
		RecordingHandler handler = new RecordingHandler();
		StorageConfig config = config("oss-cn-heyuan.aliyuncs.com", false);
		config.setDir("/integration-documents/");

		String result = handler.doUploadFile(config, new ByteArrayInputStream(new byte[] { 1 }), "guide.PDF", 1);

		assertThat(handler.aliyunCalled).isTrue();
		assertThat(handler.s3Called).isFalse();
		assertThat(handler.objectName).startsWith("integration-documents/").endsWith(".pdf");
		assertThat(result).isEqualTo("https://bucket.oss-cn-heyuan.aliyuncs.com/" + handler.objectName);
	}

	@Test
	void keepsS3CompatiblePathStyleForMinio() {
		RecordingHandler handler = new RecordingHandler();
		StorageConfig config = config("http://minio:9000/", true);

		String result = handler.doUploadFile(config, new ByteArrayInputStream(new byte[] { 1 }), "guide.pdf", 1);

		assertThat(handler.aliyunCalled).isFalse();
		assertThat(handler.s3Called).isTrue();
		assertThat(result).isEqualTo("http://minio:9000/bucket/" + handler.objectName);
	}

	@Test
	void rejectsIncompleteStorageConfigurationBeforeCreatingClient() {
		RecordingHandler handler = new RecordingHandler();
		StorageConfig config = config("oss-cn-heyuan.aliyuncs.com", false);
		config.setAccessKeySecret(null);

		assertThatThrownBy(
				() -> handler.doUploadFile(config, new ByteArrayInputStream(new byte[] { 1 }), "guide.pdf", 1))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("对象存储访问凭据未配置");
		assertThat(handler.aliyunCalled).isFalse();
		assertThat(handler.s3Called).isFalse();
	}

	private StorageConfig config(String endpoint, boolean pathStyle) {
		StorageConfig config = new StorageConfig();
		config.setAccessKeyId("access-key");
		config.setAccessKeySecret("access-secret");
		config.setEndpoint(endpoint);
		config.setBucket("bucket");
		config.setContextType("application/pdf");
		config.setStyleAccessEnabled(pathStyle);
		return config;
	}

	private static final class RecordingHandler extends S3UploadFileHandler {

		private boolean aliyunCalled;

		private boolean s3Called;

		private String objectName;

		@Override
		protected void uploadToAliyun(StorageConfig storageConfig, String endpoint, String bucketName,
				String objectName, InputStream inputStream, long size) {
			this.aliyunCalled = true;
			this.objectName = objectName;
		}

		@Override
		protected void uploadToS3(StorageConfig storageConfig, String endpoint, String bucketName, String objectName,
				InputStream inputStream, long size) {
			this.s3Called = true;
			this.objectName = objectName;
		}

	}

}
