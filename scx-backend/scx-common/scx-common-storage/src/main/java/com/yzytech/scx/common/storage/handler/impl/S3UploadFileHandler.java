package com.yzytech.scx.common.storage.handler.impl;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.yzytech.scx.common.storage.entity.StorageConfig;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

/**
 * S3协议上传文件处理器
 *
 */
@Slf4j
@Component
public class S3UploadFileHandler extends AbstractUploadFileHandler {

	@Override
	public String doUploadFile(StorageConfig storageConfig, InputStream inputStream, String fileName, long size) {
		validateStorageConfig(storageConfig, inputStream, fileName, size);
		String endpoint = normalizeEndpoint(storageConfig.getEndpoint());
		String bucketName = storageConfig.getBucket().trim();
		String objectName = buildObjectName(storageConfig.getDir(), fileName);

		if (isAliyunEndpoint(endpoint)) {
			uploadToAliyun(storageConfig, endpoint, bucketName, objectName, inputStream, size);
		}
		else {
			uploadToS3(storageConfig, endpoint, bucketName, objectName, inputStream, size);
		}
		return buildObjectUrl(storageConfig, endpoint, bucketName, objectName);
	}

	@Override
	public String getType() {
		return "oss";
	}

	@Override
	public InputStream openFile(SysStorageConfigDTO sysStorageConfig, String objectKey) {
		StorageConfig storageConfig = toStorageConfig(sysStorageConfig, null);
		validateObjectReference(storageConfig, objectKey);
		String endpoint = normalizeEndpoint(storageConfig.getEndpoint());
		String bucketName = storageConfig.getBucket().trim();
		if (isAliyunEndpoint(endpoint)) {
			return openFromAliyun(storageConfig, endpoint, bucketName, objectKey);
		}
		return openFromS3(storageConfig, endpoint, bucketName, objectKey);
	}

	@Override
	public void deleteFile(SysStorageConfigDTO sysStorageConfig, String objectKey) {
		StorageConfig storageConfig = toStorageConfig(sysStorageConfig, null);
		validateObjectReference(storageConfig, objectKey);
		String endpoint = normalizeEndpoint(storageConfig.getEndpoint());
		String bucketName = storageConfig.getBucket().trim();
		if (isAliyunEndpoint(endpoint)) {
			deleteFromAliyun(storageConfig, endpoint, bucketName, objectKey);
		}
		else {
			deleteFromS3(storageConfig, endpoint, bucketName, objectKey);
		}
	}

	protected void uploadToAliyun(StorageConfig storageConfig, String endpoint, String bucketName, String objectName,
			InputStream inputStream, long size) {
		OSS ossClient = new OSSClientBuilder().build(endpoint, storageConfig.getAccessKeyId(),
				storageConfig.getAccessKeySecret());
		try {
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(size);
			metadata.setContentType(normalizeContentType(storageConfig.getContextType()));
			com.aliyun.oss.model.PutObjectRequest request = new com.aliyun.oss.model.PutObjectRequest(bucketName,
					objectName, inputStream);
			request.setMetadata(metadata);
			ossClient.putObject(request);
		}
		catch (OSSException e) {
			log.warn("阿里云 OSS 上传失败: code={}, requestId={}, hostId={}, bucket={}", e.getErrorCode(), e.getRequestId(),
					e.getHostId(), bucketName);
			throw providerFailure(e.getErrorCode(), e);
		}
		catch (ClientException e) {
			log.warn("阿里云 OSS 客户端连接失败: endpoint={}, bucket={}, reason={}", URI.create(endpoint).getHost(), bucketName,
					e.getMessage());
			throw new IllegalStateException("对象存储连接失败，请检查 Endpoint、网络和 HTTPS 配置", e);
		}
		finally {
			ossClient.shutdown();
		}
	}

	protected void uploadToS3(StorageConfig storageConfig, String endpoint, String bucketName, String objectName,
			InputStream inputStream, long size) {
		try (S3Client s3Client = S3Client.builder()
			.endpointOverride(URI.create(endpoint))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(storageConfig.getAccessKeyId(), storageConfig.getAccessKeySecret())))
			.region(Region.US_EAST_1)
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(Boolean.TRUE.equals(storageConfig.getStyleAccessEnabled()))
				.chunkedEncodingEnabled(false)
				.build())
			.build()) {
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(objectName)
				.contentType(normalizeContentType(storageConfig.getContextType()))
				.build();
			s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, size));
		}
		catch (S3Exception e) {
			String errorCode = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
			log.warn("S3 兼容对象存储上传失败: code={}, status={}, requestId={}, endpoint={}, bucket={}", errorCode,
					e.statusCode(), e.requestId(), URI.create(endpoint).getHost(), bucketName);
			throw providerFailure(errorCode, e);
		}
		catch (SdkClientException e) {
			log.warn("S3 兼容对象存储连接失败: endpoint={}, bucket={}, reason={}", URI.create(endpoint).getHost(), bucketName,
					e.getMessage());
			throw new IllegalStateException("对象存储连接失败，请检查 Endpoint、网络和 HTTPS 配置", e);
		}
	}

	private InputStream openFromAliyun(StorageConfig config, String endpoint, String bucketName, String objectKey) {
		OSS client = new OSSClientBuilder().build(endpoint, config.getAccessKeyId(), config.getAccessKeySecret());
		try {
			OSSObject object = client.getObject(bucketName, objectKey);
			InputStream content = object.getObjectContent();
			return new FilterInputStream(content) {
				@Override
				public void close() throws IOException {
					try {
						super.close();
					}
					finally {
						client.shutdown();
					}
				}
			};
		}
		catch (OSSException e) {
			client.shutdown();
			if ("NoSuchKey".equals(e.getErrorCode())) {
				throw new IllegalStateException("存储文件不存在", e);
			}
			throw storageOperationFailure(e.getErrorCode(), e);
		}
		catch (ClientException e) {
			client.shutdown();
			throw new IllegalStateException("对象存储连接失败，请稍后重试", e);
		}
	}

	private InputStream openFromS3(StorageConfig config, String endpoint, String bucketName, String objectKey) {
		S3Client client = createS3Client(config, endpoint);
		try {
			InputStream content = client
				.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build());
			return new FilterInputStream(content) {
				@Override
				public void close() throws IOException {
					try {
						super.close();
					}
					finally {
						client.close();
					}
				}
			};
		}
		catch (NoSuchKeyException e) {
			client.close();
			throw new IllegalStateException("存储文件不存在", e);
		}
		catch (S3Exception | SdkClientException e) {
			client.close();
			throw new IllegalStateException("读取对象存储文件失败，请稍后重试", e);
		}
	}

	private void deleteFromAliyun(StorageConfig config, String endpoint, String bucketName, String objectKey) {
		OSS client = new OSSClientBuilder().build(endpoint, config.getAccessKeyId(), config.getAccessKeySecret());
		try {
			client.deleteObject(bucketName, objectKey);
		}
		catch (OSSException e) {
			if (!"NoSuchKey".equals(e.getErrorCode())) {
				throw storageOperationFailure(e.getErrorCode(), e);
			}
		}
		catch (ClientException e) {
			throw new IllegalStateException("对象存储连接失败，请稍后重试", e);
		}
		finally {
			client.shutdown();
		}
	}

	private void deleteFromS3(StorageConfig config, String endpoint, String bucketName, String objectKey) {
		try (S3Client client = createS3Client(config, endpoint)) {
			client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectKey).build());
		}
		catch (NoSuchKeyException ignored) {
			// Idempotent deletion: an already missing object is the desired result.
		}
		catch (S3Exception | SdkClientException e) {
			throw new IllegalStateException("删除对象存储文件失败，请稍后重试", e);
		}
	}

	private S3Client createS3Client(StorageConfig config, String endpoint) {
		return S3Client.builder()
			.endpointOverride(URI.create(endpoint))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(config.getAccessKeyId(), config.getAccessKeySecret())))
			.region(Region.US_EAST_1)
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(Boolean.TRUE.equals(config.getStyleAccessEnabled()))
				.chunkedEncodingEnabled(false)
				.build())
			.build();
	}

	static boolean isAliyunEndpoint(String endpoint) {
		String host = URI.create(endpoint).getHost();
		return host != null && host.toLowerCase(Locale.ROOT).endsWith(".aliyuncs.com");
	}

	static String normalizeEndpoint(String endpoint) {
		String value = endpoint.trim();
		if (!value.startsWith("http://") && !value.startsWith("https://")) {
			value = "https://" + value;
		}
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		URI uri = URI.create(value);
		if (uri.getHost() == null) {
			throw new IllegalStateException("对象存储 Endpoint 格式无效");
		}
		return value;
	}

	static String buildObjectName(String dir, String fileName) {
		String suffix = "";
		int suffixIndex = fileName.lastIndexOf('.');
		if (suffixIndex > 0 && suffixIndex < fileName.length() - 1) {
			suffix = fileName.substring(suffixIndex + 1).toLowerCase(Locale.ROOT);
		}
		String directory = StringUtils.hasText(dir) ? dir.trim().replace('\\', '/') : "";
		while (directory.startsWith("/")) {
			directory = directory.substring(1);
		}
		while (directory.endsWith("/")) {
			directory = directory.substring(0, directory.length() - 1);
		}
		if (directory.contains("..")) {
			throw new IllegalStateException("对象存储目录不能包含上级路径");
		}
		String objectFileName = UUID.randomUUID() + (StringUtils.hasText(suffix) ? "." + suffix : "");
		return StringUtils.hasText(directory) ? directory + "/" + objectFileName : objectFileName;
	}

	static String buildObjectUrl(StorageConfig storageConfig, String endpoint, String bucketName, String objectName) {
		if (StringUtils.hasText(storageConfig.getDomain())) {
			return normalizeEndpoint(storageConfig.getDomain()) + "/" + objectName;
		}
		URI endpointUri = URI.create(endpoint);
		if (isAliyunEndpoint(endpoint) || !Boolean.TRUE.equals(storageConfig.getStyleAccessEnabled())) {
			String authority = endpointUri.getPort() < 0 ? endpointUri.getHost()
					: endpointUri.getHost() + ":" + endpointUri.getPort();
			return endpointUri.getScheme() + "://" + bucketName + "." + authority + "/" + objectName;
		}
		return endpoint + "/" + bucketName + "/" + objectName;
	}

	private static void validateStorageConfig(StorageConfig storageConfig, InputStream inputStream, String fileName,
			long size) {
		if (storageConfig == null) {
			throw new IllegalStateException("对象存储配置为空");
		}
		if (!StringUtils.hasText(storageConfig.getEndpoint())) {
			throw new IllegalStateException("对象存储 Endpoint 未配置");
		}
		if (!StringUtils.hasText(storageConfig.getBucket())) {
			throw new IllegalStateException("对象存储 Bucket 未配置");
		}
		if (!StringUtils.hasText(storageConfig.getAccessKeyId())
				|| !StringUtils.hasText(storageConfig.getAccessKeySecret())) {
			throw new IllegalStateException("对象存储访问凭据未配置");
		}
		if (inputStream == null || !StringUtils.hasText(fileName) || size <= 0) {
			throw new IllegalStateException("上传文件内容为空");
		}
	}

	private static void validateObjectReference(StorageConfig storageConfig, String objectKey) {
		validateStorageConfig(storageConfig, InputStream.nullInputStream(), "managed-object", 1);
		if (!StringUtils.hasText(objectKey) || objectKey.startsWith("/") || objectKey.contains("\\")
				|| objectKey.contains("../")) {
			throw new IllegalStateException("对象存储引用无效");
		}
	}

	private static String normalizeContentType(String contentType) {
		return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
	}

	private static IllegalStateException providerFailure(String errorCode, RuntimeException cause) {
		String code = StringUtils.hasText(errorCode) ? errorCode : "Unknown";
		return switch (code) {
			case "AccessDenied" -> new IllegalStateException("对象存储拒绝上传，请检查 Bucket 写入权限", cause);
			case "InvalidAccessKeyId" -> new IllegalStateException("对象存储 AccessKey 无效或已停用", cause);
			case "SignatureDoesNotMatch" ->
				new IllegalStateException("对象存储签名校验失败，请检查 AccessSecret 和 Endpoint 地域", cause);
			case "NoSuchBucket" -> new IllegalStateException("对象存储 Bucket 不存在或与 Endpoint 地域不一致", cause);
			default -> new IllegalStateException("对象存储上传失败（" + code + "）", cause);
		};
	}

	private static IllegalStateException storageOperationFailure(String errorCode, RuntimeException cause) {
		String code = StringUtils.hasText(errorCode) ? errorCode : "Unknown";
		return switch (code) {
			case "AccessDenied" -> new IllegalStateException("对象存储拒绝访问，请检查 Bucket 权限", cause);
			case "InvalidAccessKeyId" -> new IllegalStateException("对象存储 AccessKey 无效或已停用", cause);
			case "SignatureDoesNotMatch" -> new IllegalStateException("对象存储签名校验失败", cause);
			case "NoSuchBucket" -> new IllegalStateException("对象存储 Bucket 不存在", cause);
			default -> new IllegalStateException("对象存储操作失败（" + code + "）", cause);
		};
	}

}
