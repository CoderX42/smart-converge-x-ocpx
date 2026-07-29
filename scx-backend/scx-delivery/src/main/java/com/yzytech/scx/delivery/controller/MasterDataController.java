package com.yzytech.scx.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.delivery.dto.IntegrationDocumentView;
import com.yzytech.scx.delivery.entity.*;
import com.yzytech.scx.delivery.mapper.*;
import com.yzytech.scx.delivery.service.MasterDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/ad")
public class MasterDataController {

	private final PartnerMapper partnerMapper;

	private final UpstreamCustomerMapper customerMapper;

	private final ProductMapper productMapper;

	private final MediaChannelMapper channelMapper;

	private final IntegrationDocumentMapper documentMapper;

	private final MasterDataService service;

	@GetMapping("/partners")
	@SaCheckPermission(value = { "ad:partner:read", "ad:master:read" }, mode = SaMode.OR)
	public Result<Page<Partner>> partners(Page<Partner> page, String mediaId, String keyword, String status) {
		return page(page, partnerMapper, mediaId, keyword, status, Partner::getMediaId, Partner::getCode,
				Partner::getName, Partner::getStatus);
	}

	@PostMapping("/partners")
	@SaCheckPermission(value = { "ad:partner:write", "ad:master:write" }, mode = SaMode.OR)
	@SysLog("新增合作方")
	public Result<Boolean> savePartner(@Valid @RequestBody Partner value) {
		return call(() -> service.savePartner(value));
	}

	@PutMapping("/partners/{id}")
	@SaCheckPermission(value = { "ad:partner:write", "ad:master:write" }, mode = SaMode.OR)
	@SysLog("修改合作方")
	public Result<Boolean> updatePartner(@PathVariable String id, @Valid @RequestBody Partner value) {
		value.setId(id);
		return call(() -> service.savePartner(value));
	}

	@GetMapping("/upstream-customers")
	@SaCheckPermission("ad:customer:read")
	public Result<Page<UpstreamCustomer>> customers(Page<UpstreamCustomer> page, String mediaId, String keyword,
			String status) {
		return page(page, customerMapper, mediaId, keyword, status, UpstreamCustomer::getMediaId,
				UpstreamCustomer::getCode, UpstreamCustomer::getName, UpstreamCustomer::getStatus);
	}

	@PostMapping("/upstream-customers")
	@SaCheckPermission("ad:customer:write")
	public Result<Boolean> saveCustomer(@Valid @RequestBody UpstreamCustomer value) {
		return call(() -> service.saveCustomer(value));
	}

	@PutMapping("/upstream-customers/{id}")
	@SaCheckPermission("ad:customer:write")
	public Result<Boolean> updateCustomer(@PathVariable String id, @Valid @RequestBody UpstreamCustomer value) {
		value.setId(id);
		return call(() -> service.saveCustomer(value));
	}

	@GetMapping("/products")
	@SaCheckPermission("ad:master:read")
	public Result<Page<Product>> products(Page<Product> page, String mediaId, String keyword, String status) {
		return page(page, productMapper, mediaId, keyword, status, Product::getMediaId, Product::getCode,
				Product::getName, Product::getStatus);
	}

	@PostMapping("/products")
	@SaCheckPermission("ad:master:write")
	public Result<Boolean> saveProduct(@Valid @RequestBody Product value) {
		return call(() -> service.saveProduct(value));
	}

	@PutMapping("/products/{id}")
	@SaCheckPermission("ad:master:write")
	public Result<Boolean> updateProduct(@PathVariable String id, @Valid @RequestBody Product value) {
		value.setId(id);
		return call(() -> service.saveProduct(value));
	}

	@GetMapping("/media-channels")
	@SaCheckPermission("ad:master:read")
	public Result<Page<MediaChannel>> channels(Page<MediaChannel> page, String mediaId, String keyword, String status) {
		return page(page, channelMapper, mediaId, keyword, status, MediaChannel::getMediaId, MediaChannel::getCode,
				MediaChannel::getName, MediaChannel::getStatus);
	}

	@PostMapping("/media-channels")
	@SaCheckPermission("ad:master:write")
	public Result<Boolean> saveChannel(@Valid @RequestBody MediaChannel value) {
		return call(() -> service.saveChannel(value));
	}

	@PutMapping("/media-channels/{id}")
	@SaCheckPermission("ad:master:write")
	public Result<Boolean> updateChannel(@PathVariable String id, @Valid @RequestBody MediaChannel value) {
		value.setId(id);
		return call(() -> service.saveChannel(value));
	}

	@GetMapping("/integration-documents")
	@SaCheckPermission("ad:integration-document:read")
	public Result<IPage<IntegrationDocumentView>> documents(Page<IntegrationDocument> page, String mediaId,
			String keyword, String type, String storageType, String status) {
		if (!StringUtils.hasText(mediaId) || !service.readable(mediaId))
			return Result.fail(404, "资源不存在");
		LambdaQueryWrapper<IntegrationDocument> query = Wrappers.<IntegrationDocument>lambdaQuery()
			.eq(IntegrationDocument::getMediaId, mediaId)
			.eq(StringUtils.hasText(type), IntegrationDocument::getType, type)
			.eq(StringUtils.hasText(storageType), IntegrationDocument::getStorageType, storageType)
			.eq(StringUtils.hasText(status), IntegrationDocument::getStatus, status)
			.and(StringUtils.hasText(keyword),
					q -> q.like(IntegrationDocument::getName, keyword)
						.or()
						.like(IntegrationDocument::getOriginalFileName, keyword)
						.or()
						.like(IntegrationDocument::getDescription, keyword))
			.orderByDesc(IntegrationDocument::getUpdateTime);
		return Result.success(documentMapper.selectPage(page, query).convert(IntegrationDocumentView::from));
	}

	@PostMapping(value = "/integration-documents", consumes = MediaType.APPLICATION_JSON_VALUE)
	@SaCheckPermission("ad:integration-document:write")
	@SysLog("新增接入文档")
	public Result<Boolean> saveDocument(@Valid @RequestBody IntegrationDocument value) {
		return call(() -> service.saveDocument(value));
	}

	@PostMapping(value = "/integration-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SaCheckPermission("ad:integration-document:write")
	@SysLog("新增接入文档")
	public ResponseEntity<Result<IntegrationDocumentView>> saveDocumentMultipart(
			@Valid @RequestPart("metadata") IntegrationDocument value,
			@RequestPart(value = "file", required = false) MultipartFile file) {
		return documentCall(() -> IntegrationDocumentView.from(service.saveDocument(value, file)));
	}

	@PostMapping("/integration-documents/upload")
	@SaCheckPermission("ad:integration-document:write")
	@SysLog("上传接入文档文件")
	public Result<String> uploadDocument(@RequestParam String mediaId, @RequestPart("file") MultipartFile file) {
		return callValue(() -> service.uploadDocument(mediaId, file));
	}

	@PutMapping(value = "/integration-documents/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@SaCheckPermission("ad:integration-document:write")
	@SysLog("修改接入文档")
	public Result<Boolean> updateDocument(@PathVariable String id, @Valid @RequestBody IntegrationDocument value) {
		value.setId(id);
		return call(() -> service.saveDocument(value));
	}

	@PutMapping(value = "/integration-documents/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SaCheckPermission("ad:integration-document:write")
	@SysLog("修改接入文档")
	public ResponseEntity<Result<IntegrationDocumentView>> updateDocumentMultipart(@PathVariable String id,
			@Valid @RequestPart("metadata") IntegrationDocument value,
			@RequestPart(value = "file", required = false) MultipartFile file) {
		value.setId(id);
		return documentCall(() -> IntegrationDocumentView.from(service.saveDocument(value, file)));
	}

	@GetMapping("/integration-documents/{id}/content")
	@SaCheckPermission("ad:integration-document:read")
	public ResponseEntity<StreamingResponseBody> documentContent(@PathVariable String id) {
		try {
			MasterDataService.DocumentContent content = service.openDocument(id);
			IntegrationDocument document = content.document();
			String fileName = StringUtils.hasText(document.getOriginalFileName()) ? document.getOriginalFileName()
					: document.getName();
			MediaType contentType;
			try {
				contentType = MediaType.parseMediaType(StringUtils.hasText(document.getMimeType())
						? document.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
			}
			catch (IllegalArgumentException ignored) {
				contentType = MediaType.APPLICATION_OCTET_STREAM;
			}
			StreamingResponseBody body = outputStream -> {
				try (var inputStream = content.stream()) {
					inputStream.transferTo(outputStream);
				}
			};
			ResponseEntity.BodyBuilder response = ResponseEntity.ok()
				.contentType(contentType)
				.cacheControl(CacheControl.noStore().cachePrivate())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff");
			if (document.getFileSize() != null && document.getFileSize() >= 0)
				response.contentLength(document.getFileSize());
			return response.body(body);
		}
		catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在");
		}
		catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
		}
	}

	@DeleteMapping("/integration-documents/{id}")
	@SaCheckPermission("ad:integration-document:delete")
	@SysLog("永久删除接入文档")
	public ResponseEntity<Result<Boolean>> deleteDocument(@PathVariable String id, @RequestParam Integer version) {
		try {
			return ResponseEntity.ok(Result.success(service.deleteDocument(id, version)));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(404, "资源不存在"));
		}
		catch (IllegalStateException exception) {
			HttpStatus status = exception.getMessage() != null && exception.getMessage().contains("存储")
					? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.CONFLICT;
			return ResponseEntity.status(status).body(Result.fail(status.value(), exception.getMessage()));
		}
		catch (Exception exception) {
			log.error("永久删除接入文档发生未处理异常: id={}", id, exception);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail("文档删除失败，请稍后重试"));
		}
	}

	private <T> Result<Page<T>> page(Page<T> page, BaseMapper<T> mapper, String mediaId, String keyword, String status,
			com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> media,
			com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> code,
			com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> name,
			com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> state) {
		if (!StringUtils.hasText(mediaId) || !service.readable(mediaId))
			return Result.fail(404, "资源不存在");
		LambdaQueryWrapper<T> query = Wrappers.<T>lambdaQuery()
			.eq(media, mediaId)
			.eq(StringUtils.hasText(status), state, status)
			.and(StringUtils.hasText(keyword), q -> q.like(code, keyword).or().like(name, keyword));
		return Result.success(mapper.selectPage(page, query));
	}

	private Result<Boolean> call(java.util.concurrent.Callable<Boolean> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("保存失败");
		}
	}

	private <T> Result<T> callValue(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			return Result.fail(e.getMessage());
		}
		catch (Exception e) {
			log.error("接入文档上传发生未处理异常", e);
			return Result.fail("文件上传失败");
		}
	}

	private <T> ResponseEntity<Result<T>> documentCall(java.util.concurrent.Callable<T> action) {
		try {
			return ResponseEntity.ok(Result.success(action.call()));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest()
				.body(Result.fail(HttpStatus.BAD_REQUEST.value(), exception.getMessage()));
		}
		catch (IllegalStateException exception) {
			boolean conflict = exception.getMessage() != null
					&& (exception.getMessage().contains("修改") || exception.getMessage().contains("引用"));
			HttpStatus status = conflict ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
			return ResponseEntity.status(status).body(Result.fail(status.value(), exception.getMessage()));
		}
		catch (Exception exception) {
			log.error("保存接入文档发生未处理异常", exception);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail("文档保存失败，请稍后重试"));
		}
	}

}
