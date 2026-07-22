package com.yzytech.scx.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.delivery.entity.*;
import com.yzytech.scx.delivery.mapper.*;
import com.yzytech.scx.delivery.service.MasterDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Function;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad")
public class MasterDataController {

	private final PartnerMapper partnerMapper;

	private final UpstreamCustomerMapper customerMapper;

	private final ProductMapper productMapper;

	private final MediaChannelMapper channelMapper;

	private final IntegrationDocumentMapper documentMapper;

	private final MasterDataService service;

	@GetMapping("/partners")
	@SaCheckPermission("ad:master:read")
	public Result<Page<Partner>> partners(Page<Partner> page, String mediaId, String keyword, String status) {
		return page(page, partnerMapper, mediaId, keyword, status, Partner::getMediaId, Partner::getCode,
				Partner::getName, Partner::getStatus);
	}

	@PostMapping("/partners")
	@SaCheckPermission("ad:master:write")
	public Result<Boolean> savePartner(@Valid @RequestBody Partner value) {
		return call(() -> service.savePartner(value));
	}

	@PutMapping("/partners/{id}")
	@SaCheckPermission("ad:master:write")
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
	@SaCheckPermission("ad:master:read")
	public Result<Page<IntegrationDocument>> documents(Page<IntegrationDocument> page, String mediaId, String keyword,
			String status) {
		return page(page, documentMapper, mediaId, keyword, status, IntegrationDocument::getMediaId, d -> d.getId(),
				IntegrationDocument::getName, IntegrationDocument::getStatus);
	}

	@PostMapping("/integration-documents")
	@SaCheckPermission("ad:master:write")
	public Result<Boolean> saveDocument(@Valid @RequestBody IntegrationDocument value) {
		return call(() -> service.saveDocument(value));
	}

	@PostMapping("/integration-documents/upload")
	@SaCheckPermission("ad:master:write")
	public Result<String> uploadDocument(@RequestParam String mediaId, @RequestPart("file") MultipartFile file) {
		return callValue(() -> service.uploadDocument(mediaId, file));
	}

	@PutMapping("/integration-documents/{id}")
	@SaCheckPermission("ad:master:write")
	public Result<Boolean> updateDocument(@PathVariable String id, @Valid @RequestBody IntegrationDocument value) {
		value.setId(id);
		return call(() -> service.saveDocument(value));
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
			return Result.fail("文件上传失败");
		}
	}

}
