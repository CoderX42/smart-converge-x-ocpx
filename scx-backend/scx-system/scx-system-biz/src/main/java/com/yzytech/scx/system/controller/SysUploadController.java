

package com.yzytech.scx.system.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.storage.handler.StorageFactory;
import com.yzytech.scx.system.api.dto.SysStorageConfigDTO;
import com.yzytech.scx.system.api.entity.SysMaterial;
import com.yzytech.scx.system.service.ISysMaterialService;
import com.yzytech.scx.system.service.ISysStorageConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * 文件上传
 *
 * @since 2022/2/26 16:45
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping({ "/file", "/system/file" })
@Tag(description = "file", name = "文件上传")
public class SysUploadController {

	private final ISysStorageConfigService sysStorageConfigService;

	private final StorageFactory storageFactory;

	private final ISysMaterialService sysMaterialService;

	@Operation(summary = "文件上传")
	@PostMapping("/upload")
	public Result upload(@RequestPart("file") MultipartFile file,
			@RequestParam(value = "groupId", required = false) String groupId,
			@RequestParam(value = "shopId", required = false) String shopId,
			@RequestParam(value = "type", required = false) String type) throws Exception {

		SysStorageConfigDTO sysStorageConfig = sysStorageConfigService.getConfig();
		if (ObjectUtil.isNull(sysStorageConfig)) {
			throw new RuntimeException("文件存储配置为空");
		}
		String url = storageFactory.getStrategy(sysStorageConfig.getType())
			.uploadFile(sysStorageConfig, file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
					file.getSize());

		SysMaterial sysMaterial = new SysMaterial();
		sysMaterial.setUrl(url);
		sysMaterial.setFileSize(file.getSize());
		sysMaterial.setGroupId(groupId);
		sysMaterial.setShopId(shopId);
		sysMaterial.setType(type);
		sysMaterial.setName(file.getOriginalFilename());
		sysMaterialService.save(sysMaterial);
		return Result.success(url);
	}

	@Operation(summary = "移动端文件上传")
	@PostMapping("/app/upload")
	public Result appUpload(@RequestPart("file") MultipartFile file) throws Exception {
		SysStorageConfigDTO sysStorageConfig = sysStorageConfigService.getConfig();
		if (ObjectUtil.isNull(sysStorageConfig)) {
			throw new RuntimeException("文件存储配置为空");
		}
		String url = storageFactory.getStrategy(sysStorageConfig.getType())
			.uploadFile(sysStorageConfig, file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
					file.getSize());
		return Result.success(url);
	}

	@Operation(summary = "本地文件预览/下载")
	@GetMapping("/local/{url}")
	public void getLocalFile(@PathVariable String url, HttpServletResponse response) {
		SysStorageConfigDTO sysStorageConfig = sysStorageConfigService.getConfig();
		if (ObjectUtil.isNull(sysStorageConfig)) {
			throw new RuntimeException("文件存储配置为空");
		}

		// 提取路径中的文件名部分
		String path = url;
		if (StrUtil.isBlank(path)) {
			return;
		}

		// 本地存储根路径 (Bucket字段被复用为根路径)
		String rootPath = sysStorageConfig.getBucket();
		if (StrUtil.isBlank(rootPath)) {
			throw new RuntimeException("本地存储根路径未配置");
		}

		// 拼接绝对路径并安全检查
		File file = FileUtil.file(rootPath, path);
		if (!file.exists() || !file.isFile()) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		// 设置响应头
		response.setContentType(FileUtil.getMimeType(file.getName()));
		// 如果需要强制下载，可以取消注释下面这行
		// response.setHeader("Content-Disposition", "attachment;filename=" +
		// URLEncoder.encode(file.getName(), StandardCharsets.UTF_8));

		try (FileInputStream in = new FileInputStream(file)) {
			IoUtil.copy(in, response.getOutputStream());
		}
		catch (IOException e) {
			log.error("文件读取失败", e);
		}
	}

}
