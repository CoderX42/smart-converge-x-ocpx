
package com.yzytech.scx.common.storage.handler.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.yzytech.scx.common.storage.entity.StorageConfig;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本地上传文件处理器
 *
 */
@Component
public class LocalUploadFileHandler extends AbstractUploadFileHandler {

	@Autowired
	private Environment environment;

	public String getActiveProfile() {
		String[] profiles = environment.getActiveProfiles();
		if (profiles.length > 0) {
			return profiles[0];
		}
		return "default";
	}

	public int getServerPort() {
		return Integer.parseInt(environment.getProperty("server.port"));
	}

	@SneakyThrows
	@Override
	public String doUploadFile(StorageConfig storageConfig, InputStream inputStream, String fileName, long size) {
		// 对于本地存储：
		// domain 作为访问域名 (例如 https://file.example.com)
		// bucket 作为本地存储根路径 (例如 /data/files)

		String domain = storageConfig.getDomain();
		String rootPath = storageConfig.getBucket();
		String prefix = "system";
		if (getActiveProfile().equals("dev")) {
			prefix = "boot";
		}
		// 如果 endpoint 为空，尝试自动获取
		if (!StringUtils.hasText(domain)) {
			try {
				InetAddress inet = InetAddress.getLocalHost();
				domain = "http://" + inet.getHostAddress() + ":" + getServerPort() + "/" + prefix;
			}
			catch (Exception ex) {
				domain = "http://localhost:9900" + "/" + prefix;
			}
		}

		// 自动补全协议头
		if (!domain.startsWith("http")) {
			domain = "https://" + domain;
		}
		if (domain.endsWith("/")) {
			domain = domain.substring(0, domain.length() - 1);
		}

		// 获取后缀
		String suffix = "";
		if (fileName.contains(".")) {
			suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
		}
		String uuidFileName = UUID.randomUUID() + (StringUtils.hasText(suffix) ? "." + suffix : "");

		String relativePath = uuidFileName;

		// 绝对路径
		String absolutePath = rootPath + "/" + relativePath;

		// 确保目录存在
		File destFile = new File(absolutePath);
		FileUtil.touch(destFile);

		// 写入文件
		try (FileOutputStream fos = new FileOutputStream(destFile)) {
			IoUtil.copy(inputStream, fos);
		}

		// 返回访问 URL
		return domain + "/file/local/" + relativePath;
	}

	@Override
	public String getType() {
		return "local";
	}

	@Override
	@SneakyThrows
	public InputStream openFile(SysStorageConfigDTO sysStorageConfig, String objectKey) {
		return Files.newInputStream(resolveManagedPath(sysStorageConfig, objectKey));
	}

	@Override
	@SneakyThrows
	public void deleteFile(SysStorageConfigDTO sysStorageConfig, String objectKey) {
		Files.deleteIfExists(resolveManagedPath(sysStorageConfig, objectKey));
	}

	private Path resolveManagedPath(SysStorageConfigDTO config, String objectKey) {
		if (config == null || !StringUtils.hasText(config.getBucket()) || !StringUtils.hasText(objectKey)) {
			throw new IllegalStateException("本地存储对象引用不完整");
		}
		Path root = Path.of(config.getBucket()).toAbsolutePath().normalize();
		Path relative = Path.of(objectKey);
		if (root.getNameCount() == 0 || relative.isAbsolute() || relative.normalize().startsWith("..")) {
			throw new IllegalStateException("本地存储对象键无效");
		}
		Path target = root.resolve(relative).normalize();
		if (!target.startsWith(root)) {
			throw new IllegalStateException("拒绝访问存储根目录之外的文件");
		}
		if (Files.exists(target)) {
			try {
				if (Files.isSymbolicLink(target)) {
					throw new IllegalStateException("拒绝访问符号链接存储对象");
				}
				Path realRoot = root.toRealPath();
				Path realTarget = target.toRealPath();
				if (!realTarget.startsWith(realRoot)) {
					throw new IllegalStateException("拒绝访问存储根目录之外的文件");
				}
				return realTarget;
			}
			catch (java.io.IOException exception) {
				throw new IllegalStateException("校验本地存储路径失败", exception);
			}
		}
		return target;
	}

}
