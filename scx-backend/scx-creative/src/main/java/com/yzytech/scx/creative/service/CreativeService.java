package com.yzytech.scx.creative.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.commercial.service.OrganizationScopeService;
import com.yzytech.scx.creative.entity.*;
import com.yzytech.scx.creative.mapper.*;
import com.yzytech.scx.creative.port.AdGroupScopePort;
import com.yzytech.scx.subject.service.MediaScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CreativeService {

	private final CreativeAssetMapper assetMapper;

	private final CreativeAssetRevisionMapper revisionMapper;

	private final AssetBinaryMapper binaryMapper;

	private final CreativeReviewCaseMapper reviewCaseMapper;

	private final CreativeReviewLogMapper reviewLogMapper;

	private final LandingPageMapper landingPageMapper;

	private final LandingPageRevisionMapper lpRevisionMapper;

	private final CreativeBindingMapper bindingMapper;

	private final MediaScopeService scopeService;

	private final OrganizationScopeService organizationScopeService;

	private final AdGroupScopePort adGroupScopePort;

	// ── Asset ──

	public CreativeAsset requireAsset(String id) {
		CreativeAsset a = assetMapper.selectById(id);
		if (a == null || !scopeService.canRead(a.getMediaId()) || !organizationScopeService.canRead(a.getOrgId()))
			throw new IllegalArgumentException("资源不存在");
		return a;
	}

	@Transactional
	public CreativeAsset createAsset(String mediaId, String orgId, String assetType, String assetName, String fileUrl,
			String mimeType, Long fileSize, String fileHash) {
		if (!scopeService.canWrite(mediaId))
			throw new IllegalArgumentException("资源不存在");
		organizationScopeService.requireWrite(orgId);
		if (!StringUtils.hasText(assetName) || !StringUtils.hasText(assetType) || !StringUtils.hasText(fileUrl)
				|| !StringUtils.hasText(fileHash) || fileSize == null || fileSize <= 0)
			throw new IllegalArgumentException("素材信息不完整");
		CreativeAsset a = new CreativeAsset();
		a.setMediaId(mediaId);
		a.setOrgId(orgId);
		a.setAssetType(assetType);
		a.setAssetName(assetName);
		a.setFileUrl(fileUrl);
		a.setMimeType(mimeType);
		a.setFileSize(fileSize);
		a.setFileHash(fileHash);
		a.setStatus("DRAFT");
		assetMapper.insert(a);

		// Create first revision (immutable)
		CreativeAssetRevision rev = new CreativeAssetRevision();
		rev.setAssetId(a.getId());
		rev.setVersion(1);
		rev.setFileUrl(fileUrl);
		rev.setMimeType(mimeType);
		rev.setFileSize(fileSize);
		rev.setFileHash(fileHash);
		revisionMapper.insert(rev);
		AssetBinary binary = new AssetBinary();
		binary.setRevisionId(rev.getId());
		binary.setStorageKey(fileUrl);
		binary.setStorageProvider("SYSTEM");
		binary.setContentLength(fileSize);
		binary.setContentHash(fileHash);
		binaryMapper.insert(binary);
		a.setCurrentRevisionId(rev.getId());
		assetMapper.updateById(a);
		return a;
	}

	public Page<CreativeAsset> listAssets(Page<CreativeAsset> page, String mediaId, String orgId, String status,
			String keyword) {
		if (!scopeService.canRead(mediaId))
			throw new IllegalArgumentException("资源不存在");
		organizationScopeService.requireRead(orgId);
		var q = Wrappers.<CreativeAsset>lambdaQuery()
			.eq(CreativeAsset::getMediaId, mediaId)
			.eq(CreativeAsset::getOrgId, orgId)
			.eq(status != null, CreativeAsset::getStatus, status)
			.and(keyword != null, x -> x.like(CreativeAsset::getAssetName, keyword));
		return assetMapper.selectPage(page, q.orderByDesc(CreativeAsset::getCreateTime));
	}

	public CreativeAsset getAsset(String id) {
		return requireAsset(id);
	}

	public List<CreativeAssetRevision> listAssetRevisions(String assetId) {
		requireAsset(assetId);
		return revisionMapper.selectList(Wrappers.<CreativeAssetRevision>lambdaQuery()
			.eq(CreativeAssetRevision::getAssetId, assetId)
			.orderByDesc(CreativeAssetRevision::getVersion));
	}

	// ── Review ──

	@Transactional
	public CreativeReviewCase submitForReview(String assetId) {
		CreativeAsset a = requireAsset(assetId);
		if (!scopeService.canWrite(a.getMediaId()) || !organizationScopeService.canWrite(a.getOrgId()))
			throw new IllegalArgumentException("资源不存在");
		if (!"DRAFT".equals(a.getStatus()) && !"UPLOADED".equals(a.getStatus()))
			throw new IllegalArgumentException("当前状态不可送审");
		CreativeReviewCase c = new CreativeReviewCase();
		c.setAssetId(assetId);
		c.setRevisionId(a.getCurrentRevisionId());
		c.setStatus("SUBMITTED");
		c.setSubmittedAt(LocalDateTime.now());
		c.setVirusScanStatus("PENDING");
		reviewCaseMapper.insert(c);
		a.setStatus("REVIEWING");
		assetMapper.updateById(a);
		appendReviewLog(c.getId(), null, "SUBMITTED", "提交审核");
		return c;
	}

	@Transactional
	public CreativeReviewCase reviewAsset(String caseId, String decision, String comment) {
		CreativeReviewCase c = reviewCaseMapper.selectById(caseId);
		if (c == null || !"SUBMITTED".equals(c.getStatus()))
			throw new IllegalArgumentException("审核案件不可审核");
		CreativeAsset a = requireAsset(c.getAssetId());
		if ("APPROVE".equals(decision) && !"CLEAN".equals(c.getVirusScanStatus()))
			throw new IllegalArgumentException("素材尚未通过安全扫描");
		String from = c.getStatus();
		switch (decision) {
			case "APPROVE" -> {
				c.setStatus("APPROVED");
				a.setStatus("APPROVED");
			}
			case "REJECT" -> {
				c.setStatus("REJECTED");
				a.setStatus("REJECTED");
			}
			default -> throw new IllegalArgumentException("无效审核决定");
		}
		c.setReviewedAt(LocalDateTime.now());
		c.setReviewerId(currentUserId());
		c.setReviewComment(comment);
		reviewCaseMapper.updateById(c);
		assetMapper.updateById(a);
		appendReviewLog(c.getId(), from, c.getStatus(), comment);
		return c;
	}

	public Page<CreativeReviewCase> listReviewCases(Page<CreativeReviewCase> page, String mediaId, String orgId,
			String status) {
		if (!scopeService.canRead(mediaId))
			throw new IllegalArgumentException("资源不存在");
		organizationScopeService.requireRead(orgId);
		List<String> assetIds = assetMapper
			.selectObjs(Wrappers.<CreativeAsset>lambdaQuery()
				.select(CreativeAsset::getId)
				.eq(CreativeAsset::getMediaId, mediaId)
				.eq(CreativeAsset::getOrgId, orgId))
			.stream()
			.map(String::valueOf)
			.toList();
		if (assetIds.isEmpty())
			return page;
		return reviewCaseMapper.selectPage(page,
				Wrappers.<CreativeReviewCase>lambdaQuery()
					.in(CreativeReviewCase::getAssetId, assetIds)
					.eq(StringUtils.hasText(status), CreativeReviewCase::getStatus, status)
					.orderByDesc(CreativeReviewCase::getSubmittedAt));
	}

	public List<CreativeReviewLog> listReviewLogs(String caseId) {
		CreativeReviewCase reviewCase = reviewCaseMapper.selectById(caseId);
		if (reviewCase == null)
			throw new IllegalArgumentException("资源不存在");
		requireAsset(reviewCase.getAssetId());
		return reviewLogMapper.selectList(Wrappers.<CreativeReviewLog>lambdaQuery()
			.eq(CreativeReviewLog::getCaseId, caseId)
			.orderByAsc(CreativeReviewLog::getCreatedAt));
	}

	// ── Publish / Suspend ──

	@Transactional
	public CreativeAsset publishAsset(String assetId) {
		CreativeAsset a = requireAsset(assetId);
		requireAssetWrite(a);
		if (!"APPROVED".equals(a.getStatus()))
			throw new IllegalArgumentException("只有已审核素材可发布");
		a.setStatus("PUBLISHED");
		assetMapper.updateById(a);
		return a;
	}

	@Transactional
	public CreativeAsset suspendAsset(String assetId) {
		CreativeAsset a = requireAsset(assetId);
		requireAssetWrite(a);
		if (!"PUBLISHED".equals(a.getStatus()) && !"APPROVED".equals(a.getStatus()))
			throw new IllegalArgumentException("当前状态不可暂停");
		a.setStatus("SUSPENDED");
		assetMapper.updateById(a);
		return a;
	}

	// ── Landing Page ──

	@Transactional
	public LandingPage createLandingPage(String orgId, String pageName, String pageType, String targetUrl,
			String content) {
		organizationScopeService.requireWrite(orgId);
		if (!StringUtils.hasText(pageName) || !StringUtils.hasText(pageType) || !StringUtils.hasText(targetUrl))
			throw new IllegalArgumentException("落地页信息不完整");
		LandingPage lp = new LandingPage();
		lp.setOrgId(orgId);
		lp.setPageName(pageName);
		lp.setPageType(pageType);
		lp.setTargetUrl(targetUrl);
		lp.setContent(content);
		lp.setSchemaVersion(1);
		lp.setStatus("DRAFT");
		landingPageMapper.insert(lp);

		LandingPageRevision rev = new LandingPageRevision();
		rev.setLandingPageId(lp.getId());
		rev.setVersion(1);
		rev.setTargetUrl(targetUrl);
		rev.setContent(content);
		rev.setSchemaVersion(1);
		lpRevisionMapper.insert(rev);
		lp.setCurrentRevisionId(rev.getId());
		landingPageMapper.updateById(lp);
		return lp;
	}

	public Page<LandingPage> listLandingPages(Page<LandingPage> page, String orgId) {
		organizationScopeService.requireRead(orgId);
		return landingPageMapper.selectPage(page,
				Wrappers.<LandingPage>lambdaQuery()
					.eq(LandingPage::getOrgId, orgId)
					.orderByDesc(LandingPage::getCreateTime));
	}

	public LandingPage getLandingPage(String id) {
		LandingPage page = landingPageMapper.selectById(id);
		if (page == null)
			throw new IllegalArgumentException("资源不存在");
		organizationScopeService.requireRead(page.getOrgId());
		return page;
	}

	@Transactional
	public LandingPage updateLandingPage(String id, Integer version, String pageName, String pageType, String targetUrl,
			String content) {
		LandingPage page = getLandingPage(id);
		organizationScopeService.requireWrite(page.getOrgId());
		if (!"DRAFT".equals(page.getStatus()))
			throw new IllegalArgumentException("只有草稿落地页可编辑");
		if (version == null || !version.equals(page.getSchemaVersion()))
			throw new IllegalArgumentException("数据已被更新，请刷新后重试");
		if (StringUtils.hasText(pageName))
			page.setPageName(pageName);
		if (StringUtils.hasText(pageType))
			page.setPageType(pageType);
		if (StringUtils.hasText(targetUrl))
			page.setTargetUrl(targetUrl);
		if (content != null)
			page.setContent(content);
		page.setSchemaVersion(page.getSchemaVersion() + 1);
		LandingPageRevision revision = new LandingPageRevision();
		revision.setLandingPageId(page.getId());
		revision.setVersion(page.getSchemaVersion());
		revision.setTargetUrl(page.getTargetUrl());
		revision.setContent(page.getContent());
		revision.setSchemaVersion(page.getSchemaVersion());
		lpRevisionMapper.insert(revision);
		page.setCurrentRevisionId(revision.getId());
		landingPageMapper.updateById(page);
		return page;
	}

	public List<LandingPageRevision> listLandingPageRevisions(String id) {
		getLandingPage(id);
		return lpRevisionMapper.selectList(Wrappers.<LandingPageRevision>lambdaQuery()
			.eq(LandingPageRevision::getLandingPageId, id)
			.orderByDesc(LandingPageRevision::getVersion));
	}

	// ── Binding ──

	public CreativeBinding bindCreative(String adGroupId, String assetId, String revisionId, String landingPageId) {
		adGroupScopePort.requireWritable(adGroupId);
		CreativeAsset a = requireAsset(assetId);
		requireAssetWrite(a);
		if (!"PUBLISHED".equals(a.getStatus()))
			throw new IllegalArgumentException("素材未发布");
		CreativeAssetRevision revision = revisionMapper.selectById(revisionId);
		if (revision == null || !assetId.equals(revision.getAssetId()))
			throw new IllegalArgumentException("素材版本不存在");
		if (StringUtils.hasText(landingPageId)) {
			LandingPage page = landingPageMapper.selectById(landingPageId);
			if (page == null || !a.getOrgId().equals(page.getOrgId()) || !"PUBLISHED".equals(page.getStatus()))
				throw new IllegalArgumentException("落地页不存在或未发布");
		}
		if (bindingMapper.selectCount(Wrappers.<CreativeBinding>lambdaQuery()
			.eq(CreativeBinding::getAdGroupId, adGroupId)
			.eq(CreativeBinding::getCreativeAssetId, assetId)) > 0)
			throw new IllegalArgumentException("该广告组已绑定该素材");
		CreativeBinding b = new CreativeBinding();
		b.setAdGroupId(adGroupId);
		b.setCreativeAssetId(assetId);
		b.setRevisionId(revisionId);
		b.setLandingPageId(landingPageId);
		b.setStatus("ACTIVE");
		bindingMapper.insert(b);
		return b;
	}

	public CreativeBinding requireBinding(String id, String adGroupId) {
		adGroupScopePort.requireReadable(adGroupId);
		CreativeBinding binding = bindingMapper.selectById(id);
		if (binding == null || !"ACTIVE".equals(binding.getStatus()) || !adGroupId.equals(binding.getAdGroupId()))
			throw new IllegalArgumentException("创意绑定不存在");
		CreativeAsset asset = requireAsset(binding.getCreativeAssetId());
		if (!"PUBLISHED".equals(asset.getStatus()))
			throw new IllegalArgumentException("绑定素材未发布");
		CreativeAssetRevision revision = revisionMapper.selectById(binding.getRevisionId());
		if (revision == null || !asset.getId().equals(revision.getAssetId()))
			throw new IllegalArgumentException("绑定素材版本不存在");
		if (StringUtils.hasText(binding.getLandingPageId())) {
			LandingPage page = landingPageMapper.selectById(binding.getLandingPageId());
			if (page == null || !asset.getOrgId().equals(page.getOrgId()) || !"PUBLISHED".equals(page.getStatus()))
				throw new IllegalArgumentException("绑定落地页未发布");
		}
		return binding;
	}

	public Page<CreativeBinding> listBindings(Page<CreativeBinding> page, String adGroupId) {
		adGroupScopePort.requireReadable(adGroupId);
		return bindingMapper.selectPage(page,
				Wrappers.<CreativeBinding>lambdaQuery()
					.eq(CreativeBinding::getAdGroupId, adGroupId)
					.orderByDesc(CreativeBinding::getCreateTime));
	}

	@Transactional
	public CreativeBinding deactivateBinding(String id) {
		CreativeBinding binding = bindingMapper.selectById(id);
		if (binding == null)
			throw new IllegalArgumentException("资源不存在");
		CreativeAsset asset = requireAsset(binding.getCreativeAssetId());
		requireAssetWrite(asset);
		if (!"ACTIVE".equals(binding.getStatus()))
			throw new IllegalArgumentException("绑定已停用");
		binding.setStatus("INACTIVE");
		bindingMapper.updateById(binding);
		return binding;
	}

	@Transactional
	public CreativeReviewCase recordVirusScan(String caseId, String result) {
		if (!java.util.Set.of("CLEAN", "INFECTED", "ERROR").contains(result))
			throw new IllegalArgumentException("无效扫描结果");
		CreativeReviewCase reviewCase = reviewCaseMapper.selectById(caseId);
		if (reviewCase == null || !"SUBMITTED".equals(reviewCase.getStatus()))
			throw new IllegalArgumentException("审核案件不可更新");
		CreativeAsset asset = requireAsset(reviewCase.getAssetId());
		requireAssetWrite(asset);
		reviewCase.setVirusScanStatus(result);
		reviewCaseMapper.updateById(reviewCase);
		appendReviewLog(caseId, "SUBMITTED", "SUBMITTED", "安全扫描:" + result);
		return reviewCase;
	}

	@Transactional
	public LandingPage publishLandingPage(String id) {
		LandingPage page = landingPageMapper.selectById(id);
		if (page == null)
			throw new IllegalArgumentException("资源不存在");
		organizationScopeService.requireWrite(page.getOrgId());
		if (!"DRAFT".equals(page.getStatus()))
			throw new IllegalArgumentException("当前状态不可发布");
		page.setStatus("PUBLISHED");
		landingPageMapper.updateById(page);
		return page;
	}

	// ── Helpers ──

	private void appendReviewLog(String caseId, String from, String to, String comment) {
		CreativeReviewLog log = new CreativeReviewLog();
		log.setCaseId(caseId);
		log.setFromStatus(from);
		log.setToStatus(to);
		log.setOperatorId(currentUserId());
		log.setOperatorName(currentUserName());
		log.setComment(comment);
		reviewLogMapper.insert(log);
	}

	private void requireAssetWrite(CreativeAsset asset) {
		if (!scopeService.canWrite(asset.getMediaId()) || !organizationScopeService.canWrite(asset.getOrgId()))
			throw new IllegalArgumentException("资源不存在");
	}

	private String currentUserId() {
		try {
			var u = SecurityUtils.getUser();
			return u != null ? u.getUserId() : "system";
		}
		catch (Exception e) {
			return "system";
		}
	}

	private String currentUserName() {
		try {
			var u = SecurityUtils.getUser();
			return u != null ? u.getUsername() : "system";
		}
		catch (Exception e) {
			return "system";
		}
	}

}
