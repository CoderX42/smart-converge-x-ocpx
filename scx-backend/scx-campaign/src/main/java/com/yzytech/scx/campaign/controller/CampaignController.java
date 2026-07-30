package com.yzytech.scx.campaign.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.campaign.entity.*;
import com.yzytech.scx.campaign.dto.CampaignDetailView;
import com.yzytech.scx.campaign.service.CampaignService;
import com.yzytech.scx.common.core.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ad/campaign")
@Tag(name = "投放计划", description = "Campaign/AdGroup/Ad 投放控制面")
public class CampaignController {

	private final CampaignService service;

	@PostMapping("/campaigns")
	@SaCheckPermission("campaign:write")
	@Operation(summary = "创建推广计划")
	public Result<Campaign> create(@RequestParam String adAccountId, @RequestParam String name,
			@RequestParam String objective, @RequestParam String billingMode,
			@RequestParam(required = false) BigDecimal targetCpa,
			@RequestParam(required = false) BigDecimal totalBudget,
			@RequestParam(required = false) BigDecimal dailyBudget,
			@RequestParam(required = false) LocalDateTime startAt,
			@RequestParam(required = false) LocalDateTime endAt) {
		return call(() -> service.createCampaign(adAccountId, name, objective, billingMode, targetCpa, totalBudget,
				dailyBudget, startAt, endAt));
	}

	@GetMapping("/campaigns")
	@SaCheckPermission("campaign:read")
	public Result<Page<Campaign>> list(Page<Campaign> page, @RequestParam String adAccountId, String status,
			String keyword) {
		return call(() -> service.listCampaigns(page, adAccountId, status, keyword));
	}

	@GetMapping("/campaigns/{id}")
	@SaCheckPermission("campaign:read")
	public Result<Campaign> get(@PathVariable String id) {
		return call(() -> service.requireCampaign(id));
	}

	@GetMapping("/campaigns/{id}/detail")
	@SaCheckPermission("campaign:read")
	public Result<CampaignDetailView> detail(@PathVariable String id) {
		return call(() -> service.getCampaignDetail(id));
	}

	@PutMapping("/campaigns/{id}")
	@SaCheckPermission("campaign:write")
	public Result<Campaign> update(@PathVariable String id, @RequestParam Integer version,
			@RequestParam(required = false) String name, @RequestParam(required = false) String objective,
			@RequestParam(required = false) String billingMode, @RequestParam(required = false) BigDecimal targetCpa,
			@RequestParam(required = false) BigDecimal totalBudget,
			@RequestParam(required = false) BigDecimal dailyBudget,
			@RequestParam(required = false) LocalDateTime startAt,
			@RequestParam(required = false) LocalDateTime endAt) {
		return call(() -> service.updateCampaign(id, version, name, objective, billingMode, targetCpa, totalBudget,
				dailyBudget, startAt, endAt));
	}

	@GetMapping("/campaigns/{id}/validate")
	@SaCheckPermission("campaign:read")
	@Operation(summary = "发布前校验")
	public Result<List<String>> validate(@PathVariable String id) {
		return call(() -> service.validateForPublishing(id));
	}

	@PostMapping("/campaigns/{id}/submit")
	@SaCheckPermission("campaign:write")
	public Result<Campaign> submit(@PathVariable String id) {
		return call(() -> service.submitForApproval(id));
	}

	@PostMapping("/campaigns/{id}/approve")
	@SaCheckPermission("campaign:review")
	public Result<Campaign> approve(@PathVariable String id) {
		return call(() -> service.approve(id));
	}

	@PostMapping("/campaigns/{id}/reject")
	@SaCheckPermission("campaign:review")
	public Result<Campaign> reject(@PathVariable String id) {
		return call(() -> service.reject(id));
	}

	@PostMapping("/campaigns/{id}/publish")
	@SaCheckPermission("campaign:write")
	public Result<Campaign> publish(@PathVariable String id) {
		return call(() -> service.publish(id));
	}

	@PostMapping("/campaigns/{id}/pause")
	@SaCheckPermission("campaign:write")
	public Result<Campaign> pause(@PathVariable String id) {
		return call(() -> service.pause(id));
	}

	@PostMapping("/campaigns/{id}/resume")
	@SaCheckPermission("campaign:write")
	public Result<Campaign> resume(@PathVariable String id) {
		return call(() -> service.resume(id));
	}

	@PostMapping("/campaigns/{id}/end")
	@SaCheckPermission("campaign:write")
	public Result<Campaign> end(@PathVariable String id) {
		return call(() -> service.end(id));
	}

	@PostMapping("/campaigns/{id}/archive")
	@SaCheckPermission("campaign:write")
	public Result<Campaign> archive(@PathVariable String id) {
		return call(() -> service.archive(id));
	}

	@PostMapping("/ad-groups")
	@SaCheckPermission("campaign:write")
	@Operation(summary = "创建广告组（含优化目标、预算计划、定向版本）")
	public Result<AdGroup> createAdGroup(@RequestParam String campaignId, @RequestParam String name,
			@RequestParam String eventCode, @RequestParam(required = false) BigDecimal targetCpa,
			@RequestParam(required = false) BigDecimal totalBudget,
			@RequestParam(required = false) BigDecimal dailyBudget,
			@RequestParam(required = false) String targetingAst) {
		return call(() -> service.createAdGroup(campaignId, name, eventCode, targetCpa, totalBudget, dailyBudget,
				targetingAst));
	}

	@GetMapping("/ad-groups")
	@SaCheckPermission("campaign:read")
	public Result<List<AdGroup>> listAdGroups(@RequestParam String campaignId) {
		return call(() -> service.listAdGroups(campaignId));
	}

	@PutMapping("/ad-groups/{id}")
	@SaCheckPermission("campaign:write")
	public Result<AdGroup> updateAdGroup(@PathVariable String id, @RequestParam Integer version,
			@RequestParam(required = false) String name, @RequestParam(required = false) BigDecimal bidCap) {
		return call(() -> service.updateAdGroup(id, version, name, bidCap));
	}

	@PostMapping("/ad-groups/{id}/archive")
	@SaCheckPermission("campaign:write")
	public Result<AdGroup> archiveAdGroup(@PathVariable String id, @RequestParam Integer version) {
		return call(() -> service.archiveAdGroup(id, version));
	}

	@PostMapping("/ads")
	@SaCheckPermission("campaign:write")
	public Result<Ad> createAd(@RequestParam String adGroupId, @RequestParam String name,
			@RequestParam(required = false) String creativeBindingId) {
		return call(() -> service.createAd(adGroupId, name, creativeBindingId));
	}

	@GetMapping("/ads")
	@SaCheckPermission("campaign:read")
	public Result<List<Ad>> listAds(@RequestParam String adGroupId) {
		return call(() -> service.listAds(adGroupId));
	}

	@PutMapping("/ads/{id}")
	@SaCheckPermission("campaign:write")
	public Result<Ad> updateAd(@PathVariable String id, @RequestParam Integer version,
			@RequestParam(required = false) String name, @RequestParam(required = false) String creativeBindingId) {
		return call(() -> service.updateAd(id, version, name, creativeBindingId));
	}

	@PostMapping("/ads/{id}/archive")
	@SaCheckPermission("campaign:write")
	public Result<Ad> archiveAd(@PathVariable String id, @RequestParam Integer version) {
		return call(() -> service.archiveAd(id, version));
	}

	@PostMapping("/campaigns/{id}/bind-media")
	@SaCheckPermission("campaign:write")
	@Operation(summary = "绑定媒体广告位")
	public Result<MediaPublishingBinding> bindMedia(@PathVariable String id, @RequestParam String mediaAccountId,
			@RequestParam String placementId, @RequestParam String trackingTaskId, @RequestParam String adGroupId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveFrom,
			@RequestParam(required = false) @DateTimeFormat(
					iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveTo,
			@RequestParam String bindingStatus) {
		return call(() -> service.bindMedia(id, mediaAccountId, placementId, trackingTaskId, adGroupId, effectiveFrom,
				effectiveTo, bindingStatus));
	}

	@GetMapping("/campaigns/{id}/media-bindings")
	@SaCheckPermission("campaign:read")
	public Result<Page<MediaPublishingBinding>> mediaBindings(@PathVariable String id,
			Page<MediaPublishingBinding> page) {
		return call(() -> service.listMediaBindings(page, id));
	}

	@DeleteMapping("/media-bindings/{id}")
	@SaCheckPermission("campaign:write")
	public Result<Boolean> unbindMedia(@PathVariable String id) {
		return call(() -> {
			service.unbindMedia(id);
			return true;
		});
	}

	@GetMapping("/campaigns/{id}/operations")
	@SaCheckPermission("campaign:read")
	public Result<Page<PublishingOperation>> operations(@PathVariable String id, Page<PublishingOperation> page) {
		return call(() -> service.listPublishingOperations(page, id));
	}

	@GetMapping("/campaigns/{id}/revisions")
	@SaCheckPermission("campaign:read")
	public Result<Page<CampaignRevision>> revisions(@PathVariable String id, Page<CampaignRevision> page) {
		return call(() -> service.listCampaignRevisions(page, id));
	}

	private <T> Result<T> call(java.util.concurrent.Callable<T> action) {
		try {
			return Result.success(action.call());
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		catch (Exception e) {
			return Result.fail("操作失败");
		}
	}

}
