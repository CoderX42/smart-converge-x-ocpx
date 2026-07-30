package com.yzytech.scx.campaign.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.campaign.entity.*;
import com.yzytech.scx.campaign.dto.CampaignDetailView;
import com.yzytech.scx.campaign.mapper.*;
import com.yzytech.scx.commercial.entity.AdAccount;
import com.yzytech.scx.commercial.port.PublishingEligibilityPort;
import com.yzytech.scx.commercial.service.CommercialService;
import com.yzytech.scx.creative.entity.CreativeBinding;
import com.yzytech.scx.creative.service.CreativeService;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.finance.service.FinanceService;
import com.yzytech.scx.inventory.dto.PublishRequest;
import com.yzytech.scx.inventory.dto.PublishResult;
import com.yzytech.scx.inventory.entity.MediaAccount;
import com.yzytech.scx.inventory.service.MediaAdapterService;
import com.yzytech.scx.inventory.service.InventoryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CampaignService {

	private static final Set<String> OBJECTIVES = Set.of("TRAFFIC", "CONVERSION", "APP_INSTALL", "SALES");

	private static final Set<String> BILLING_MODES = Set.of("CPM", "CPC", "MEDIA_MANAGED_OCPC");

	private static final Set<String> EVENT_CODES = Set.of("CLICK", "LANDING", "ACTIVATE", "REGISTER", "PAY");

	private final CampaignMapper campaignMapper;

	private final CampaignRevisionMapper campaignRevisionMapper;

	private final AdGroupMapper adGroupMapper;

	private final AdGroupRevisionMapper adGroupRevisionMapper;

	private final AdMapper adMapper;

	private final AdRevisionMapper adRevisionMapper;

	private final OptimizationGoalMapper goalMapper;

	private final BudgetPlanMapper budgetPlanMapper;

	private final TargetingRevisionMapper targetingMapper;

	private final MediaPublishingBindingMapper publishingBindingMapper;

	private final PublishingOperationMapper publishingOpMapper;

	// Cross-module dependencies
	private final CommercialService commercialService;

	private final FinanceService financeService;

	private final InventoryService inventoryService;

	private final MediaAdapterService mediaAdapterService;

	private final TrackingTaskMapper trackingTaskMapper;

	private final CreativeService creativeService;

	private final PublishingEligibilityPort publishingEligibilityPort;

	private final ObjectMapper objectMapper;

	// ═══ Campaign ═══

	public Campaign requireCampaign(String id) {
		Campaign c = campaignMapper.selectById(id);
		if (c == null)
			throw new IllegalArgumentException("资源不存在");
		commercialService.requireAdAccount(c.getAdAccountId());
		return c;
	}

	private Campaign requireWritableCampaign(String id) {
		Campaign campaign = requireCampaign(id);
		commercialService.requireWritableAdAccount(campaign.getAdAccountId());
		return campaign;
	}

	@Transactional
	public Campaign createCampaign(String adAccountId, String name, String objective, String billingMode,
			BigDecimal targetCpa, BigDecimal totalBudget, BigDecimal dailyBudget, LocalDateTime startAt,
			LocalDateTime endAt) {
		commercialService.requireWritableAdAccount(adAccountId);
		validateCampaignInput(name, objective, billingMode, targetCpa, totalBudget, dailyBudget, startAt, endAt);
		Campaign c = new Campaign();
		c.setAdAccountId(adAccountId);
		c.setName(name);
		c.setObjective(objective);
		c.setBillingMode(billingMode);
		c.setTargetCpa(targetCpa);
		c.setTotalBudget(totalBudget);
		c.setDailyBudget(dailyBudget);
		c.setCurrency("CNY");
		c.setTimezone("Asia/Shanghai");
		c.setStartAt(startAt);
		c.setEndAt(endAt);
		c.setStatus("DRAFT");
		c.setVersion(1);
		campaignMapper.insert(c);
		snapshotCampaign(c);
		return c;
	}

	public Page<Campaign> listCampaigns(Page<Campaign> page, String adAccountId, String status, String keyword) {
		if (!StringUtils.hasText(adAccountId))
			throw new IllegalArgumentException("广告账户不能为空");
		commercialService.requireAdAccount(adAccountId);
		var q = Wrappers.<Campaign>lambdaQuery()
			.eq(Campaign::getAdAccountId, adAccountId)
			.eq(StringUtils.hasText(status), Campaign::getStatus, status)
			.and(StringUtils.hasText(keyword), x -> x.like(Campaign::getName, keyword));
		return campaignMapper.selectPage(page, q.orderByDesc(Campaign::getCreateTime));
	}

	@Transactional
	public Campaign updateCampaign(String id, Integer version, String name, String objective, String billingMode,
			BigDecimal targetCpa, BigDecimal totalBudget, BigDecimal dailyBudget, LocalDateTime startAt,
			LocalDateTime endAt) {
		Campaign current = requireWritableCampaign(id);
		if (!"DRAFT".equals(current.getStatus()))
			throw new IllegalArgumentException("只有草稿计划可编辑");
		if (version == null || !version.equals(current.getVersion()))
			throw new IllegalArgumentException("数据已被更新，请刷新后重试");
		String nextName = StringUtils.hasText(name) ? name : current.getName();
		String nextObjective = StringUtils.hasText(objective) ? objective : current.getObjective();
		String nextBillingMode = StringUtils.hasText(billingMode) ? billingMode : current.getBillingMode();
		BigDecimal nextTargetCpa = targetCpa != null ? targetCpa : current.getTargetCpa();
		BigDecimal nextTotalBudget = totalBudget != null ? totalBudget : current.getTotalBudget();
		BigDecimal nextDailyBudget = dailyBudget != null ? dailyBudget : current.getDailyBudget();
		LocalDateTime nextStartAt = startAt != null ? startAt : current.getStartAt();
		LocalDateTime nextEndAt = endAt != null ? endAt : current.getEndAt();
		validateCampaignInput(nextName, nextObjective, nextBillingMode, nextTargetCpa, nextTotalBudget, nextDailyBudget,
				nextStartAt, nextEndAt);
		int rows = campaignMapper.update(null,
				Wrappers.<Campaign>lambdaUpdate()
					.eq(Campaign::getId, id)
					.eq(Campaign::getVersion, version)
					.set(Campaign::getName, nextName)
					.set(Campaign::getObjective, nextObjective)
					.set(Campaign::getBillingMode, nextBillingMode)
					.set(Campaign::getTargetCpa, nextTargetCpa)
					.set(Campaign::getTotalBudget, nextTotalBudget)
					.set(Campaign::getDailyBudget, nextDailyBudget)
					.set(Campaign::getStartAt, nextStartAt)
					.set(Campaign::getEndAt, nextEndAt)
					.set(Campaign::getVersion, version + 1));
		if (rows != 1)
			throw new IllegalArgumentException("数据已被更新，请刷新后重试");
		Campaign updated = campaignMapper.selectById(id);
		snapshotCampaign(updated);
		return updated;
	}

	public CampaignDetailView getCampaignDetail(String id) {
		Campaign campaign = requireCampaign(id);
		List<AdGroup> groups = listAdGroups(id);
		List<Ad> ads = groups.stream().flatMap(group -> listAds(group.getId()).stream()).toList();
		return new CampaignDetailView(campaign, groups, ads, listPublishingBindings(id), validateForPublishing(id));
	}

	// ═══ Pre-publish validation chain ═══

	public List<String> validateForPublishing(String campaignId) {
		List<String> errors = new ArrayList<>();
		Campaign c = requireCampaign(campaignId);
		// 1. Commercial eligibility (account, advertiser, qualification, agreement,
		// authorization).
		var acct = commercialService.requireAdAccount(c.getAdAccountId());
		var eligibility = publishingEligibilityPort.evaluate(c.getAdAccountId());
		if (!eligibility.eligible())
			errors.add("商业发布条件未满足: " + String.join(",", eligibility.reasonCodes()));
		String settlementOrgId = settlementOrganizationId(acct);
		// 2. Balance and budget.
		if (c.getTotalBudget() == null || c.getTotalBudget().signum() <= 0)
			errors.add("计划总预算必须大于零");
		if (c.getTotalBudget() != null) {
			BigDecimal avail = financeService.getAvailableBalance(settlementOrgId);
			if (avail.compareTo(c.getTotalBudget()) < 0)
				errors.add("可用余额不足: " + avail + " < " + c.getTotalBudget());
		}
		// 3. Time range
		if (c.getEndAt() != null && !c.getEndAt().isAfter(LocalDateTime.now()))
			errors.add("结束时间已过");
		if (c.getStartAt() != null && c.getEndAt() != null && !c.getStartAt().isBefore(c.getEndAt()))
			errors.add("开始时间必须早于结束时间");
		// 4. Target CPA
		if (c.getTargetCpa() != null && c.getTargetCpa().compareTo(BigDecimal.ZERO) <= 0)
			errors.add("目标CPA必须大于零");
		// 5. Has ad groups with creatives
		List<AdGroup> groups = adGroupMapper
			.selectList(Wrappers.<AdGroup>lambdaQuery().eq(AdGroup::getCampaignId, campaignId));
		if (groups.isEmpty())
			errors.add("至少需要一个广告组");
		for (AdGroup group : groups) {
			OptimizationGoal goal = goalMapper.selectById(group.getOptimizationGoalId());
			if (goal == null || !EVENT_CODES.contains(goal.getEventCode()))
				errors.add("广告组目标事件不可用: " + group.getId());
			List<Ad> ads = adMapper.selectList(Wrappers.<Ad>lambdaQuery().eq(Ad::getAdGroupId, group.getId()));
			if (ads.isEmpty()) {
				errors.add("广告组至少需要一个广告: " + group.getId());
			}
			for (Ad ad : ads) {
				if (!StringUtils.hasText(ad.getCreativeBindingId())) {
					errors.add("广告缺少创意绑定: " + ad.getId());
					continue;
				}
				try {
					creativeService.requireBinding(ad.getCreativeBindingId(), group.getId());
				}
				catch (IllegalArgumentException invalidCreative) {
					errors.add("广告创意不可发布: " + ad.getId());
				}
			}
		}
		List<MediaPublishingBinding> mediaBindings = listPublishingBindings(campaignId);
		if (mediaBindings.isEmpty())
			errors.add("至少需要绑定一个媒体广告位");
		for (MediaPublishingBinding binding : mediaBindings) {
			try {
				inventoryService.requirePlacementForMediaAccount(binding.getPlacementId(), binding.getMediaAccountId());
				var capabilities = mediaAdapterService.getCapabilities(binding.getMediaAccountId());
				if (!capabilities.billingModes().contains(c.getBillingMode()))
					errors.add("媒体不支持计费方式: " + binding.getMediaAccountId());
				for (AdGroup group : groups) {
					OptimizationGoal goal = goalMapper.selectById(group.getOptimizationGoalId());
					if (goal != null && !capabilities.targetEvents().contains(goal.getEventCode()))
						errors.add("媒体不支持目标事件: " + goal.getEventCode());
				}
			}
			catch (IllegalArgumentException | IllegalStateException invalidBinding) {
				errors.add("媒体广告位不可用: " + binding.getId());
			}
		}
		return errors;
	}

	// ═══ Status transitions ═══

	@Transactional
	public Campaign submitForApproval(String campaignId) {
		Campaign c = requireWritableCampaign(campaignId);
		if (!"DRAFT".equals(c.getStatus()))
			throw new IllegalArgumentException("只有草稿可提交审批");
		List<String> errors = validateForPublishing(campaignId);
		if (!errors.isEmpty())
			throw new IllegalArgumentException("发布校验失败: " + String.join("; ", errors));
		c.setStatus("PENDING_REVIEW");
		campaignMapper.updateById(c);
		return c;
	}

	@Transactional
	public Campaign approve(String campaignId) {
		Campaign c = requireCampaign(campaignId);
		if (!"PENDING_REVIEW".equals(c.getStatus()))
			throw new IllegalArgumentException("只有待审批可批准");
		c.setStatus("APPROVED");
		campaignMapper.updateById(c);
		return c;
	}

	@Transactional
	public Campaign reject(String campaignId) {
		Campaign c = requireCampaign(campaignId);
		if (!"PENDING_REVIEW".equals(c.getStatus()))
			throw new IllegalArgumentException("只有待审批可拒绝");
		c.setStatus("DRAFT");
		campaignMapper.updateById(c);
		return c;
	}

	@Transactional
	public Campaign publish(String campaignId) {
		Campaign c = requireWritableCampaign(campaignId);
		if (!"APPROVED".equals(c.getStatus()))
			throw new IllegalArgumentException("只有已批准可发布");
		List<String> errors = validateForPublishing(campaignId);
		if (!errors.isEmpty())
			throw new IllegalArgumentException("发布校验失败: " + String.join("; ", errors));
		AdAccount account = commercialService.requireAdAccount(c.getAdAccountId());
		String settlementOrgId = settlementOrganizationId(account);
		var reservation = financeService.reserveBudget(settlementOrgId, c.getTotalBudget(), c.getAdAccountId(),
				"CAMPAIGN_PUBLISH_" + c.getId() + "_V" + c.getVersion(), "计划发布预算预留:" + c.getName());
		c.setBudgetReservationId(reservation.getId());
		PublishingOperation op = new PublishingOperation();
		op.setCampaignId(campaignId);
		op.setOperationType("PUBLISH");
		op.setStatus("RUNNING");
		op.setRequestSnapshot(snapshot(Map.of("campaignId", c.getId(), "version", c.getVersion())));
		op.setCreatedAt(LocalDateTime.now());
		publishingOpMapper.insert(op);
		List<String> externalIds = new ArrayList<>();
		for (MediaPublishingBinding binding : listPublishingBindings(campaignId)) {
			PublishResult result = mediaAdapterService.publishCampaign(binding.getMediaAccountId(),
					new PublishRequest(c.getId(), binding.getExternalCampaignId(), binding.getPlacementId(),
							Map.of("billingMode", c.getBillingMode()),
							"PUBLISH_" + c.getId() + "_" + binding.getId() + "_V" + c.getVersion()));
			if (!result.success())
				throw new IllegalStateException("媒体发布失败: " + result.errorMessage());
			binding.setExternalCampaignId(result.externalCampaignId());
			binding.setExternalStatus(result.externalStatus());
			binding.setLastSyncedAt(result.syncedAt());
			publishingBindingMapper.updateById(binding);
			externalIds.add(result.externalCampaignId());
		}
		c.setStatus("DELIVERING");
		campaignMapper.updateById(c);
		op.setStatus("COMPLETED");
		op.setResultDetail(snapshot(Map.of("externalCampaignIds", externalIds)));
		op.setCompletedAt(LocalDateTime.now());
		publishingOpMapper.updateById(op);
		return c;
	}

	@Transactional
	public Campaign pause(String campaignId) {
		Campaign c = requireWritableCampaign(campaignId);
		if (!"DELIVERING".equals(c.getStatus()))
			throw new IllegalArgumentException("只有投放中可暂停");
		changeExternalStatus(c, "PAUSED");
		c.setStatus("PAUSED");
		campaignMapper.updateById(c);
		return c;
	}

	@Transactional
	public Campaign resume(String campaignId) {
		Campaign c = requireWritableCampaign(campaignId);
		if (!"PAUSED".equals(c.getStatus()))
			throw new IllegalArgumentException("只有已暂停可继续");
		changeExternalStatus(c, "DELIVERING");
		c.setStatus("DELIVERING");
		campaignMapper.updateById(c);
		return c;
	}

	@Transactional
	public Campaign end(String campaignId) {
		Campaign c = requireWritableCampaign(campaignId);
		if (!"DELIVERING".equals(c.getStatus()) && !"PAUSED".equals(c.getStatus()))
			throw new IllegalArgumentException("投放中或已暂停才能结束");
		changeExternalStatus(c, "ENDED");
		c.setStatus("ENDED");
		campaignMapper.updateById(c);
		if (StringUtils.hasText(c.getBudgetReservationId())) {
			AdAccount account = commercialService.requireAdAccount(c.getAdAccountId());
			financeService.releaseRemainingReservation(settlementOrganizationId(account), c.getBudgetReservationId(),
					"CAMPAIGN_END_" + c.getId(), "计划结束释放剩余预算:" + c.getName());
		}
		return c;
	}

	@Transactional
	public Campaign archive(String campaignId) {
		Campaign c = requireWritableCampaign(campaignId);
		if (!"ENDED".equals(c.getStatus()))
			throw new IllegalArgumentException("只有已结束可归档");
		c.setStatus("ARCHIVED");
		campaignMapper.updateById(c);
		return c;
	}

	// ═══ AdGroup ═══

	@Transactional
	public AdGroup createAdGroup(String campaignId, String name, String eventCode, BigDecimal targetCpa,
			BigDecimal totalBudget, BigDecimal dailyBudget, String targetingAst) {
		Campaign c = requireWritableCampaign(campaignId);
		if ("ENDED".equals(c.getStatus()) || "ARCHIVED".equals(c.getStatus()))
			throw new IllegalArgumentException("计划已结束或归档，不能新增广告组");
		if (!StringUtils.hasText(name) || !EVENT_CODES.contains(eventCode))
			throw new IllegalArgumentException("广告组名称或目标事件无效");
		validateBudget(totalBudget, dailyBudget);
		validateTargeting(targetingAst);

		OptimizationGoal goal = new OptimizationGoal();
		goal.setEventCode(eventCode);
		goal.setTargetCpa(targetCpa);
		goal.setTargetCpaCurrency("CNY");
		goal.setAttributionWindowHours(168);
		goal.setStatus("ACTIVE");
		goalMapper.insert(goal);

		BudgetPlan bp = new BudgetPlan();
		bp.setTotalAmount(totalBudget);
		bp.setDailyAmount(dailyBudget);
		bp.setPacingStrategy("STANDARD");
		bp.setCurrency("CNY");
		bp.setStatus("ACTIVE");
		budgetPlanMapper.insert(bp);

		TargetingRevision tr = new TargetingRevision();
		tr.setTargetingAst(targetingAst != null ? targetingAst : "{}");
		tr.setVersion(1);
		targetingMapper.insert(tr);

		AdGroup ag = new AdGroup();
		ag.setCampaignId(campaignId);
		ag.setName(name);
		ag.setStatus("DRAFT");
		ag.setOptimizationGoalId(goal.getId());
		ag.setBudgetPlanId(bp.getId());
		ag.setTargetingRevisionId(tr.getId());
		ag.setVersion(1);
		adGroupMapper.insert(ag);
		snapshotAdGroup(ag);
		return ag;
	}

	public List<AdGroup> listAdGroups(String campaignId) {
		requireCampaign(campaignId);
		return adGroupMapper.selectList(Wrappers.<AdGroup>lambdaQuery()
			.eq(AdGroup::getCampaignId, campaignId)
			.orderByDesc(AdGroup::getCreateTime));
	}

	@Transactional
	public AdGroup updateAdGroup(String id, Integer version, String name, BigDecimal bidCap) {
		AdGroup group = adGroupMapper.selectById(id);
		if (group == null)
			throw new IllegalArgumentException("资源不存在");
		Campaign campaign = requireWritableCampaign(group.getCampaignId());
		if (!"DRAFT".equals(campaign.getStatus()))
			throw new IllegalArgumentException("只有草稿计划可编辑广告组");
		if (version == null || !version.equals(group.getVersion()))
			throw new IllegalArgumentException("数据已被更新，请刷新后重试");
		String nextName = StringUtils.hasText(name) ? name : group.getName();
		int rows = adGroupMapper.update(null,
				Wrappers.<AdGroup>lambdaUpdate()
					.eq(AdGroup::getId, id)
					.eq(AdGroup::getVersion, version)
					.set(AdGroup::getName, nextName)
					.set(bidCap != null, AdGroup::getBidCap, bidCap)
					.set(AdGroup::getVersion, version + 1));
		if (rows != 1)
			throw new IllegalArgumentException("数据已被更新，请刷新后重试");
		AdGroup updated = adGroupMapper.selectById(id);
		snapshotAdGroup(updated);
		return updated;
	}

	@Transactional
	public AdGroup archiveAdGroup(String id, Integer version) {
		AdGroup group = updateAdGroup(id, version, null, null);
		group.setStatus("ARCHIVED");
		adGroupMapper.updateById(group);
		return group;
	}

	// ═══ Ad ═══

	@Transactional
	public Ad createAd(String adGroupId, String name, String creativeBindingId) {
		AdGroup ag = adGroupMapper.selectById(adGroupId);
		if (ag == null)
			throw new IllegalArgumentException("资源不存在");
		requireWritableCampaign(ag.getCampaignId());
		if (!StringUtils.hasText(name))
			throw new IllegalArgumentException("广告名称不能为空");
		if (creativeBindingId != null) {
			creativeService.requireBinding(creativeBindingId, adGroupId);
		}
		Ad ad = new Ad();
		ad.setAdGroupId(adGroupId);
		ad.setName(name);
		ad.setStatus("DRAFT");
		ad.setCreativeBindingId(creativeBindingId);
		ad.setVersion(1);
		adMapper.insert(ad);
		snapshotAd(ad);
		return ad;
	}

	public List<Ad> listAds(String adGroupId) {
		AdGroup group = adGroupMapper.selectById(adGroupId);
		if (group == null)
			throw new IllegalArgumentException("资源不存在");
		requireCampaign(group.getCampaignId());
		return adMapper
			.selectList(Wrappers.<Ad>lambdaQuery().eq(Ad::getAdGroupId, adGroupId).orderByDesc(Ad::getCreateTime));
	}

	@Transactional
	public Ad updateAd(String id, Integer version, String name, String creativeBindingId) {
		Ad ad = adMapper.selectById(id);
		if (ad == null)
			throw new IllegalArgumentException("资源不存在");
		AdGroup group = adGroupMapper.selectById(ad.getAdGroupId());
		if (group == null)
			throw new IllegalArgumentException("资源不存在");
		Campaign campaign = requireWritableCampaign(group.getCampaignId());
		if (!"DRAFT".equals(campaign.getStatus()))
			throw new IllegalArgumentException("只有草稿计划可编辑广告");
		if (version == null || !version.equals(ad.getVersion()))
			throw new IllegalArgumentException("数据已被更新，请刷新后重试");
		if (StringUtils.hasText(creativeBindingId))
			creativeService.requireBinding(creativeBindingId, group.getId());
		String nextName = StringUtils.hasText(name) ? name : ad.getName();
		String nextBinding = creativeBindingId != null ? creativeBindingId : ad.getCreativeBindingId();
		int rows = adMapper.update(null,
				Wrappers.<Ad>lambdaUpdate()
					.eq(Ad::getId, id)
					.eq(Ad::getVersion, version)
					.set(Ad::getName, nextName)
					.set(Ad::getCreativeBindingId, nextBinding)
					.set(Ad::getVersion, version + 1));
		if (rows != 1)
			throw new IllegalArgumentException("数据已被更新，请刷新后重试");
		Ad updated = adMapper.selectById(id);
		snapshotAd(updated);
		return updated;
	}

	@Transactional
	public Ad archiveAd(String id, Integer version) {
		Ad ad = updateAd(id, version, null, null);
		ad.setStatus("ARCHIVED");
		adMapper.updateById(ad);
		return ad;
	}

	// ═══ Publishing bindings ═══

	@Transactional
	public MediaPublishingBinding bindMedia(String campaignId, String mediaAccountId, String placementId,
			String trackingTaskId, String adGroupId, LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
			String bindingStatus) {
		requireWritableCampaign(campaignId);
		if (effectiveFrom == null || (effectiveTo != null && !effectiveFrom.isBefore(effectiveTo)))
			throw new IllegalArgumentException("绑定生效区间无效");
		if (!Set.of("ACTIVE", "INACTIVE").contains(bindingStatus))
			throw new IllegalArgumentException("绑定状态无效");
		MediaAccount mediaAccount = inventoryService.requireMediaAccount(mediaAccountId);
		inventoryService.requirePlacementForMediaAccount(placementId, mediaAccountId);
		TrackingTask task = trackingTaskMapper.selectById(trackingTaskId);
		if (task == null || !"ACTIVE".equals(task.getStatus()) || !mediaAccount.getMediaId().equals(task.getMediaId()))
			throw new IllegalArgumentException("监测任务不可用或不属于同一媒体范围");
		AdGroup adGroup = adGroupMapper.selectById(adGroupId);
		if (adGroup == null || !campaignId.equals(adGroup.getCampaignId())
				|| !StringUtils.hasText(adGroup.getOptimizationGoalId()))
			throw new IllegalArgumentException("广告组不可用或尚未配置优化目标");
		if ("ACTIVE".equals(bindingStatus)) {
			var overlap = Wrappers.<MediaPublishingBinding>lambdaQuery()
				.eq(MediaPublishingBinding::getTrackingTaskId, trackingTaskId)
				.eq(MediaPublishingBinding::getBindingStatus, "ACTIVE")
				.and(q -> q.isNull(MediaPublishingBinding::getEffectiveTo)
					.or()
					.gt(MediaPublishingBinding::getEffectiveTo, effectiveFrom));
			if (effectiveTo != null)
				overlap.lt(MediaPublishingBinding::getEffectiveFrom, effectiveTo);
			if (publishingBindingMapper.selectCount(overlap) > 0)
				throw new IllegalArgumentException("监测任务在该时段已绑定其他有效媒体计划");
		}
		if (publishingBindingMapper.selectCount(Wrappers.<MediaPublishingBinding>lambdaQuery()
			.eq(MediaPublishingBinding::getCampaignId, campaignId)
			.eq(MediaPublishingBinding::getMediaAccountId, mediaAccountId)
			.eq(MediaPublishingBinding::getPlacementId, placementId)) > 0)
			throw new IllegalArgumentException("媒体广告位已绑定");
		MediaPublishingBinding b = new MediaPublishingBinding();
		b.setCampaignId(campaignId);
		b.setMediaAccountId(mediaAccountId);
		b.setPlacementId(placementId);
		b.setTrackingTaskId(trackingTaskId);
		b.setAdGroupId(adGroupId);
		b.setEffectiveFrom(effectiveFrom);
		b.setEffectiveTo(effectiveTo);
		b.setBindingStatus(bindingStatus);
		b.setVersion(0);
		publishingBindingMapper.insert(b);
		return b;
	}

	public Page<MediaPublishingBinding> listMediaBindings(Page<MediaPublishingBinding> page, String campaignId) {
		requireCampaign(campaignId);
		return publishingBindingMapper.selectPage(page,
				Wrappers.<MediaPublishingBinding>lambdaQuery()
					.eq(MediaPublishingBinding::getCampaignId, campaignId)
					.orderByDesc(MediaPublishingBinding::getLastSyncedAt));
	}

	@Transactional
	public void unbindMedia(String id) {
		MediaPublishingBinding binding = publishingBindingMapper.selectById(id);
		if (binding == null)
			throw new IllegalArgumentException("资源不存在");
		Campaign campaign = requireWritableCampaign(binding.getCampaignId());
		if (!"DRAFT".equals(campaign.getStatus()))
			throw new IllegalArgumentException("只有草稿计划可解除媒体绑定");
		binding.setBindingStatus("INACTIVE");
		binding.setEffectiveTo(LocalDateTime.now(ZoneOffset.UTC));
		publishingBindingMapper.updateById(binding);
	}

	public Page<PublishingOperation> listPublishingOperations(Page<PublishingOperation> page, String campaignId) {
		requireCampaign(campaignId);
		return publishingOpMapper.selectPage(page,
				Wrappers.<PublishingOperation>lambdaQuery()
					.eq(PublishingOperation::getCampaignId, campaignId)
					.orderByDesc(PublishingOperation::getCreatedAt));
	}

	public Page<CampaignRevision> listCampaignRevisions(Page<CampaignRevision> page, String campaignId) {
		requireCampaign(campaignId);
		return campaignRevisionMapper.selectPage(page,
				Wrappers.<CampaignRevision>lambdaQuery()
					.eq(CampaignRevision::getCampaignId, campaignId)
					.orderByDesc(CampaignRevision::getVersion));
	}

	// ═══ Helpers ═══

	private void snapshotCampaign(Campaign c) {
		CampaignRevision rev = new CampaignRevision();
		rev.setCampaignId(c.getId());
		rev.setVersion(c.getVersion());
		rev.setSnapshot(snapshot(c));
		rev.setCreatedAt(LocalDateTime.now());
		campaignRevisionMapper.insert(rev);
		c.setCurrentRevisionId(rev.getId());
		campaignMapper.updateById(c);
	}

	private void snapshotAdGroup(AdGroup group) {
		AdGroupRevision revision = new AdGroupRevision();
		revision.setAdGroupId(group.getId());
		revision.setVersion(group.getVersion());
		revision.setSnapshot(snapshot(group));
		revision.setCreatedAt(LocalDateTime.now());
		adGroupRevisionMapper.insert(revision);
		group.setCurrentRevisionId(revision.getId());
		adGroupMapper.updateById(group);
	}

	private void snapshotAd(Ad ad) {
		AdRevision revision = new AdRevision();
		revision.setAdId(ad.getId());
		revision.setVersion(ad.getVersion());
		revision.setSnapshot(snapshot(ad));
		revision.setCreatedAt(LocalDateTime.now());
		adRevisionMapper.insert(revision);
		ad.setCurrentRevisionId(revision.getId());
		adMapper.updateById(ad);
	}

	private List<MediaPublishingBinding> listPublishingBindings(String campaignId) {
		return publishingBindingMapper.selectList(
				Wrappers.<MediaPublishingBinding>lambdaQuery().eq(MediaPublishingBinding::getCampaignId, campaignId));
	}

	private void changeExternalStatus(Campaign campaign, String newStatus) {
		for (MediaPublishingBinding binding : listPublishingBindings(campaign.getId())) {
			if (!StringUtils.hasText(binding.getExternalCampaignId()))
				throw new IllegalStateException("媒体发布标识缺失");
			PublishResult result = mediaAdapterService.changeStatus(binding.getMediaAccountId(),
					binding.getExternalCampaignId(), newStatus,
					"STATUS_" + campaign.getId() + "_" + binding.getId() + "_" + newStatus);
			if (!result.success())
				throw new IllegalStateException("媒体状态变更失败: " + result.errorMessage());
			binding.setExternalStatus(result.externalStatus());
			binding.setLastSyncedAt(result.syncedAt());
			publishingBindingMapper.updateById(binding);
		}
	}

	private String settlementOrganizationId(AdAccount account) {
		return StringUtils.hasText(account.getResponsibleOrgId()) ? account.getResponsibleOrgId()
				: account.getAdvertiserOrgId();
	}

	private void validateCampaignInput(String name, String objective, String billingMode, BigDecimal targetCpa,
			BigDecimal totalBudget, BigDecimal dailyBudget, LocalDateTime startAt, LocalDateTime endAt) {
		if (!StringUtils.hasText(name) || !OBJECTIVES.contains(objective) || !BILLING_MODES.contains(billingMode))
			throw new IllegalArgumentException("计划名称、目标或计费方式无效");
		if (targetCpa != null && targetCpa.signum() <= 0)
			throw new IllegalArgumentException("目标CPA必须大于零");
		validateBudget(totalBudget, dailyBudget);
		if (startAt != null && endAt != null && !startAt.isBefore(endAt))
			throw new IllegalArgumentException("开始时间必须早于结束时间");
	}

	private void validateBudget(BigDecimal totalBudget, BigDecimal dailyBudget) {
		if (totalBudget != null && totalBudget.signum() <= 0)
			throw new IllegalArgumentException("总预算必须大于零");
		if (dailyBudget != null && dailyBudget.signum() <= 0)
			throw new IllegalArgumentException("日预算必须大于零");
		if (totalBudget != null && dailyBudget != null && dailyBudget.compareTo(totalBudget) > 0)
			throw new IllegalArgumentException("日预算不能超过总预算");
	}

	private void validateTargeting(String targetingAst) {
		if (!StringUtils.hasText(targetingAst))
			return;
		try {
			JsonNode node = objectMapper.readTree(targetingAst);
			if (!node.isObject())
				throw new IllegalArgumentException("定向条件必须是JSON对象");
		}
		catch (JsonProcessingException invalidJson) {
			throw new IllegalArgumentException("定向条件不是有效JSON");
		}
	}

	private String snapshot(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException serializationFailure) {
			throw new IllegalStateException("版本快照生成失败", serializationFailure);
		}
	}

}
