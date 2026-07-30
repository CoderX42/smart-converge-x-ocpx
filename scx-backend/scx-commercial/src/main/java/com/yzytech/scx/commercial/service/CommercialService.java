package com.yzytech.scx.commercial.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.commercial.dto.*;
import com.yzytech.scx.commercial.entity.*;
import com.yzytech.scx.commercial.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core commercial service: organization, advertiser, agency relation, ad account.
 */
@Service
@RequiredArgsConstructor
public class CommercialService {

	private static final Set<String> ORGANIZATION_TYPES = Set.of("ENTERPRISE", "BUSINESS_UNIT", "DIRECT_ADVERTISER",
			"AGENCY", "AGENCY_SUB_ACCOUNT", "MANAGED_ADVERTISER");

	private static final Set<String> ADVERTISER_STATUSES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "ARCHIVED");

	private static final Set<String> ACCOUNT_STATUSES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "CLOSED");

	private final OrganizationMapper orgMapper;

	private final AdvertiserMapper advertiserMapper;

	private final AgencyRelationMapper agencyRelationMapper;

	private final AdAccountMapper adAccountMapper;

	private final QualificationCaseMapper qualificationCaseMapper;

	private final OrganizationScopeService scopeService;

	// ── Organization ──

	public Organization requireOrg(String orgId) {
		Organization org = orgMapper.selectById(orgId);
		if (org == null || !scopeService.canRead(orgId)) {
			throw new IllegalArgumentException("资源不存在");
		}
		return org;
	}

	public Organization createOrganization(String name, String orgType, String parentOrgId, String contactName,
			String contactPhone, String contactEmail) {
		if (!scopeService.isAdmin()) {
			throw new IllegalArgumentException("资源不存在");
		}
		if (!ORGANIZATION_TYPES.contains(orgType)) {
			throw new IllegalArgumentException("无效组织类型");
		}
		if (StringUtils.hasText(parentOrgId) && orgMapper.selectById(parentOrgId) == null) {
			throw new IllegalArgumentException("上级组织不存在");
		}
		if (orgMapper.selectCount(Wrappers.<Organization>lambdaQuery().eq(Organization::getName, name)) > 0)
			throw new IllegalArgumentException("组织名称已存在");
		Organization org = new Organization();
		org.setName(name);
		org.setOrgType(orgType);
		org.setParentOrgId(parentOrgId);
		org.setStatus("ACTIVE");
		org.setContactName(contactName);
		org.setContactPhone(contactPhone);
		org.setContactEmail(contactEmail);
		orgMapper.insert(org);
		return org;
	}

	@Transactional
	public Organization updateOrganization(String id, String name, String orgType, String parentOrgId,
			String legalPerson, String contactName, String contactPhone, String contactEmail) {
		Organization org = requireOrg(id);
		scopeService.requireWrite(id);
		if (!StringUtils.hasText(name) || !ORGANIZATION_TYPES.contains(orgType)) {
			throw new IllegalArgumentException("组织名称或类型无效");
		}
		if (StringUtils.hasText(parentOrgId)) {
			if (id.equals(parentOrgId)) {
				throw new IllegalArgumentException("组织不能作为自己的上级");
			}
			requireOrg(parentOrgId);
		}
		org.setName(name.trim());
		org.setOrgType(orgType);
		org.setParentOrgId(parentOrgId);
		org.setLegalPerson(legalPerson);
		org.setContactName(contactName);
		org.setContactPhone(contactPhone);
		org.setContactEmail(contactEmail);
		orgMapper.updateById(org);
		return org;
	}

	@Transactional
	public Organization updateOrganizationStatus(String id, String status) {
		Organization org = requireOrg(id);
		scopeService.requireWrite(id);
		if ("ARCHIVED".equals(org.getStatus())) {
			throw new IllegalArgumentException("已归档组织不可重新启用");
		}
		if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
			throw new IllegalArgumentException("组织状态无效");
		}
		org.setStatus(status);
		orgMapper.updateById(org);
		return org;
	}

	@Transactional
	public void archiveOrganization(String id) {
		Organization org = requireOrg(id);
		scopeService.requireWrite(id);
		if ("ENTERPRISE".equals(org.getOrgType())) {
			throw new IllegalArgumentException("企业根组织不能删除");
		}
		long activeChildren = orgMapper.selectCount(Wrappers.<Organization>lambdaQuery()
			.eq(Organization::getParentOrgId, id)
			.eq(Organization::getStatus, "ACTIVE"));
		long activeRelations = agencyRelationMapper.selectCount(Wrappers.<AgencyRelation>lambdaQuery()
			.and(q -> q.eq(AgencyRelation::getParentAgencyOrgId, id).or().eq(AgencyRelation::getChildOrgId, id))
			.eq(AgencyRelation::getStatus, "ACTIVE"));
		long openAccounts = adAccountMapper.selectCount(Wrappers.<AdAccount>lambdaQuery()
			.eq(AdAccount::getAdvertiserOrgId, id)
			.ne(AdAccount::getStatus, "CLOSED"));
		if (activeChildren + activeRelations + openAccounts > 0) {
			throw new IllegalArgumentException("组织仍有启用的下级、代理关系或广告账户，请先处理关联数据");
		}
		org.setStatus("ARCHIVED");
		orgMapper.updateById(org);
	}

	public Page<Organization> listOrganizations(Page<Organization> page, String orgType, String keyword) {
		List<String> readableIds = scopeService.readableOrganizationIds();
		if (!scopeService.isAdmin() && readableIds.isEmpty()) {
			page.setRecords(List.of());
			page.setTotal(0);
			return page;
		}
		var q = Wrappers.<Organization>lambdaQuery()
			.in(!scopeService.isAdmin(), Organization::getId, readableIds)
			.eq(StringUtils.hasText(orgType), Organization::getOrgType, orgType)
			.and(StringUtils.hasText(keyword), x -> x.like(Organization::getName, keyword))
			.orderByDesc(Organization::getCreateTime)
			.orderByDesc(Organization::getId);
		return orgMapper.selectPage(page, q);
	}

	// ── Advertiser ──

	@Transactional
	public Advertiser createAdvertiser(CreateAdvertiserRequest req) {
		if (!scopeService.isAdmin()) {
			throw new IllegalArgumentException("资源不存在");
		}
		// Create the organization first
		Organization org = createOrganization(req.orgName(), req.orgType(), null, req.contactName(), req.contactPhone(),
				req.contactEmail());

		Advertiser advertiser = new Advertiser();
		advertiser.setOrgId(org.getId());
		advertiser.setAdvertiserName(req.advertiserName());
		advertiser.setStatus("DRAFT");
		advertiser.setIndustry(req.industry());
		advertiser.setWebsiteUrl(req.websiteUrl());
		advertiserMapper.insert(advertiser);
		return advertiser;
	}

	public Advertiser requireAdvertiser(String id) {
		Advertiser a = advertiserMapper.selectById(id);
		if (a == null || !scopeService.canRead(a.getOrgId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		return a;
	}

	public Page<Advertiser> listAdvertisers(Page<Advertiser> page, String status, String keyword) {
		List<String> readableIds = scopeService.readableOrganizationIds();
		if (!scopeService.isAdmin() && readableIds.isEmpty()) {
			page.setRecords(List.of());
			page.setTotal(0);
			return page;
		}
		var q = Wrappers.<Advertiser>lambdaQuery()
			.in(!scopeService.isAdmin(), Advertiser::getOrgId, readableIds)
			.eq(StringUtils.hasText(status), Advertiser::getStatus, status)
			.and(StringUtils.hasText(keyword), x -> x.like(Advertiser::getAdvertiserName, keyword))
			.orderByDesc(Advertiser::getCreateTime)
			.orderByDesc(Advertiser::getId);
		return advertiserMapper.selectPage(page, q);
	}

	public void updateAdvertiserStatus(String id, String newStatus) {
		Advertiser a = requireAdvertiser(id);
		scopeService.requireWrite(a.getOrgId());
		if ("ARCHIVED".equals(a.getStatus()) && !"ARCHIVED".equals(newStatus)) {
			throw new IllegalArgumentException("已归档广告主不可重新启用");
		}
		if (!ADVERTISER_STATUSES.contains(newStatus)) {
			throw new IllegalArgumentException("无效广告主状态");
		}
		if ("ACTIVE".equals(newStatus) && !hasApprovedQualification(a.getOrgId()))
			throw new IllegalArgumentException("广告主资质未通过审核，无法激活");
		a.setStatus(newStatus);
		advertiserMapper.updateById(a);
	}

	public Advertiser updateAdvertiserProfile(String id, UpdateAdvertiserRequest request) {
		Advertiser advertiser = requireAdvertiser(id);
		scopeService.requireWrite(advertiser.getOrgId());
		advertiser.setAdvertiserName(request.advertiserName());
		advertiser.setIndustry(request.industry());
		advertiser.setWebsiteUrl(request.websiteUrl());
		advertiserMapper.updateById(advertiser);
		return advertiser;
	}

	public void archiveAdvertiser(String id) {
		Advertiser advertiser = requireAdvertiser(id);
		scopeService.requireWrite(advertiser.getOrgId());
		long openAccounts = adAccountMapper.selectCount(Wrappers.<AdAccount>lambdaQuery()
			.eq(AdAccount::getAdvertiserOrgId, advertiser.getOrgId())
			.ne(AdAccount::getStatus, "CLOSED"));
		if (openAccounts > 0) {
			throw new IllegalArgumentException("广告主仍有未关闭的广告账户");
		}
		advertiser.setStatus("ARCHIVED");
		advertiserMapper.updateById(advertiser);
	}

	// ── Agency Relation ──

	@Transactional
	public AgencyRelation createAgencyRelation(CreateAgencyRelationRequest req) {
		scopeService.requireWrite(req.parentAgencyOrgId());
		scopeService.requireWrite(req.childOrgId());
		Organization parent = requireOrgInternal(req.parentAgencyOrgId());
		Organization child = requireOrgInternal(req.childOrgId());

		if (!"AGENCY".equals(parent.getOrgType()) && !"ENTERPRISE".equals(parent.getOrgType()))
			throw new IllegalArgumentException("上级组织不是代理商类型");
		if (parent.getId().equals(child.getId()))
			throw new IllegalArgumentException("不能建立自身代理关系");

		// Enforce max 2 levels: check if parent already has a parent (i.e., is already
		// sub-agency)
		long parentDepth = countAgencyDepth(parent.getId());
		if (parentDepth >= 2)
			throw new IllegalArgumentException("代理层级不能超过二级");

		// Prevent cycles
		if (isAncestor(child.getId(), parent.getId()))
			throw new IllegalArgumentException("代理关系不能形成环路");

		// Check duplicate
		if (agencyRelationMapper.selectCount(Wrappers.<AgencyRelation>lambdaQuery()
			.eq(AgencyRelation::getParentAgencyOrgId, req.parentAgencyOrgId())
			.eq(AgencyRelation::getChildOrgId, req.childOrgId())) > 0)
			throw new IllegalArgumentException("代理关系已存在");
		if (agencyRelationMapper.selectCount(Wrappers.<AgencyRelation>lambdaQuery()
			.eq(AgencyRelation::getChildOrgId, req.childOrgId())
			.eq(AgencyRelation::getStatus, "ACTIVE")) > 0)
			throw new IllegalArgumentException("下级组织已有生效代理关系");

		AgencyRelation rel = new AgencyRelation();
		rel.setParentAgencyOrgId(req.parentAgencyOrgId());
		rel.setChildOrgId(req.childOrgId());
		rel.setRelationType(StringUtils.hasText(req.relationType()) ? req.relationType() : "DIRECT");
		rel.setAuthorizedScopes(req.authorizedScopes());
		rel.setStartDate(req.startDate());
		rel.setEndDate(req.endDate());
		rel.setStatus("ACTIVE");
		agencyRelationMapper.insert(rel);
		return rel;
	}

	public void revokeAgencyRelation(String id) {
		AgencyRelation rel = agencyRelationMapper.selectById(id);
		if (rel == null)
			throw new IllegalArgumentException("资源不存在");
		scopeService.requireWrite(rel.getParentAgencyOrgId());
		scopeService.requireWrite(rel.getChildOrgId());
		rel.setStatus("REVOKED");
		agencyRelationMapper.updateById(rel);
	}

	public AgencyRelation updateAgencyRelation(String id, CreateAgencyRelationRequest req) {
		AgencyRelation rel = agencyRelationMapper.selectById(id);
		if (rel == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		scopeService.requireWrite(rel.getParentAgencyOrgId());
		scopeService.requireWrite(rel.getChildOrgId());
		if (!rel.getParentAgencyOrgId().equals(req.parentAgencyOrgId())
				|| !rel.getChildOrgId().equals(req.childOrgId())) {
			throw new IllegalArgumentException("代理双方不可编辑，请撤销后重新建立关系");
		}
		if (!"ACTIVE".equals(rel.getStatus())) {
			throw new IllegalArgumentException("只有生效中的代理关系可编辑");
		}
		if (req.endDate() != null && req.startDate() != null && req.endDate().isBefore(req.startDate())) {
			throw new IllegalArgumentException("失效日期不能早于生效日期");
		}
		rel.setRelationType(StringUtils.hasText(req.relationType()) ? req.relationType() : "DIRECT");
		rel.setAuthorizedScopes(req.authorizedScopes());
		rel.setStartDate(req.startDate());
		rel.setEndDate(req.endDate());
		agencyRelationMapper.updateById(rel);
		return rel;
	}

	public List<AgencyRelation> listAgencyRelations(String parentOrgId) {
		if (StringUtils.hasText(parentOrgId)) {
			scopeService.requireRead(parentOrgId);
		}
		List<String> readableIds = scopeService.readableOrganizationIds();
		if (!scopeService.isAdmin() && readableIds.isEmpty()) {
			return List.of();
		}
		return agencyRelationMapper.selectList(Wrappers.<AgencyRelation>lambdaQuery()
			.in(!scopeService.isAdmin(), AgencyRelation::getParentAgencyOrgId, readableIds)
			.eq(StringUtils.hasText(parentOrgId), AgencyRelation::getParentAgencyOrgId, parentOrgId)
			.orderByDesc(AgencyRelation::getCreateTime));
	}

	public Page<AgencyRelation> pageAgencyRelations(Page<AgencyRelation> page, String parentOrgId, String status) {
		if (StringUtils.hasText(parentOrgId))
			scopeService.requireRead(parentOrgId);
		List<String> readableIds = scopeService.readableOrganizationIds();
		if (!scopeService.isAdmin() && readableIds.isEmpty())
			return page;
		var query = Wrappers.<AgencyRelation>lambdaQuery()
			.in(!scopeService.isAdmin(), AgencyRelation::getParentAgencyOrgId, readableIds)
			.in(!scopeService.isAdmin(), AgencyRelation::getChildOrgId, readableIds)
			.eq(StringUtils.hasText(parentOrgId), AgencyRelation::getParentAgencyOrgId, parentOrgId)
			.eq(StringUtils.hasText(status), AgencyRelation::getStatus, status)
			.orderByDesc(AgencyRelation::getCreateTime);
		return agencyRelationMapper.selectPage(page, query);
	}

	private long countAgencyDepth(String orgId) {
		long depth = 0;
		String current = orgId;
		while (current != null && depth < 10) {
			AgencyRelation parent = agencyRelationMapper.selectOne(Wrappers.<AgencyRelation>lambdaQuery()
				.eq(AgencyRelation::getChildOrgId, current)
				.eq(AgencyRelation::getStatus, "ACTIVE")
				.last("LIMIT 1"));
			if (parent == null)
				break;
			current = parent.getParentAgencyOrgId();
			depth++;
		}
		return depth;
	}

	private boolean isAncestor(String potentialAncestor, String orgId) {
		String current = orgId;
		int guard = 0;
		while (current != null && guard++ < 100) {
			if (current.equals(potentialAncestor))
				return true;
			AgencyRelation parent = agencyRelationMapper.selectOne(Wrappers.<AgencyRelation>lambdaQuery()
				.eq(AgencyRelation::getChildOrgId, current)
				.eq(AgencyRelation::getStatus, "ACTIVE")
				.last("LIMIT 1"));
			if (parent == null)
				break;
			current = parent.getParentAgencyOrgId();
		}
		return false;
	}

	// ── Ad Account ──

	public AdAccount createAdAccount(CreateAdAccountRequest req) {
		scopeService.requireWrite(req.advertiserOrgId());
		Advertiser advertiser = requireAdvertiserByOrgId(req.advertiserOrgId());
		if (!"ACTIVE".equals(advertiser.getStatus())) {
			throw new IllegalArgumentException("广告主未激活");
		}
		validateResponsibleOrganizations(req);
		AdAccount acct = new AdAccount();
		acct.setAdvertiserOrgId(req.advertiserOrgId());
		acct.setAccountName(req.accountName());
		acct.setStatus("DRAFT");
		acct.setCurrency(StringUtils.hasText(req.currency()) ? req.currency() : "CNY");
		acct.setTimezone(StringUtils.hasText(req.timezone()) ? req.timezone() : "Asia/Shanghai");
		acct.setResponsibleOrgId(
				StringUtils.hasText(req.responsibleOrgId()) ? req.responsibleOrgId() : req.advertiserOrgId());
		acct.setRebateOrgId(req.rebateOrgId());
		acct.setRefundOrgId(req.refundOrgId());
		adAccountMapper.insert(acct);
		return acct;
	}

	public AdAccount requireAdAccount(String id) {
		AdAccount a = adAccountMapper.selectById(id);
		if (a == null || !scopeService.canRead(a.getAdvertiserOrgId())) {
			throw new IllegalArgumentException("资源不存在");
		}
		return a;
	}

	public AdAccount requireWritableAdAccount(String id) {
		AdAccount account = requireAdAccount(id);
		scopeService.requireWrite(account.getAdvertiserOrgId());
		return account;
	}

	public Page<AdAccount> listAdAccounts(Page<AdAccount> page, String advertiserOrgId, String status, String keyword) {
		if (StringUtils.hasText(advertiserOrgId)) {
			scopeService.requireRead(advertiserOrgId);
		}
		List<String> readableIds = scopeService.readableOrganizationIds();
		if (!scopeService.isAdmin() && readableIds.isEmpty()) {
			page.setRecords(List.of());
			page.setTotal(0);
			return page;
		}
		var q = Wrappers.<AdAccount>lambdaQuery()
			.in(!scopeService.isAdmin(), AdAccount::getAdvertiserOrgId, readableIds)
			.eq(StringUtils.hasText(advertiserOrgId), AdAccount::getAdvertiserOrgId, advertiserOrgId)
			.eq(StringUtils.hasText(status), AdAccount::getStatus, status)
			.and(StringUtils.hasText(keyword), x -> x.like(AdAccount::getAccountName, keyword))
			.orderByDesc(AdAccount::getCreateTime)
			.orderByDesc(AdAccount::getId);
		return adAccountMapper.selectPage(page, q);
	}

	public void updateAdAccountStatus(String id, String newStatus) {
		AdAccount a = requireAdAccount(id);
		scopeService.requireWrite(a.getAdvertiserOrgId());
		if ("CLOSED".equals(a.getStatus()) && !"CLOSED".equals(newStatus)) {
			throw new IllegalArgumentException("已关闭广告账户不可重新启用");
		}
		if (!ACCOUNT_STATUSES.contains(newStatus)) {
			throw new IllegalArgumentException("无效广告账户状态");
		}
		if ("ACTIVE".equals(newStatus)) {
			Advertiser adv = advertiserMapper
				.selectOne(Wrappers.<Advertiser>lambdaQuery().eq(Advertiser::getOrgId, a.getAdvertiserOrgId()));
			if (adv == null || !"ACTIVE".equals(adv.getStatus()))
				throw new IllegalArgumentException("广告主未激活");
		}
		a.setStatus(newStatus);
		adAccountMapper.updateById(a);
	}

	public AdAccount updateAdAccountName(String id, String accountName) {
		AdAccount account = requireAdAccount(id);
		scopeService.requireWrite(account.getAdvertiserOrgId());
		if (!StringUtils.hasText(accountName)) {
			throw new IllegalArgumentException("账户名称不能为空");
		}
		account.setAccountName(accountName);
		adAccountMapper.updateById(account);
		return account;
	}

	public AdAccount updateAdAccount(String id, UpdateAdAccountRequest request) {
		AdAccount account = requireAdAccount(id);
		scopeService.requireWrite(account.getAdvertiserOrgId());
		if (!StringUtils.hasText(request.accountName())) {
			throw new IllegalArgumentException("账户名称不能为空");
		}
		CreateAdAccountRequest validationRequest = new CreateAdAccountRequest(account.getAdvertiserOrgId(),
				request.accountName(), request.currency(), request.timezone(), request.responsibleOrgId(),
				request.rebateOrgId(), request.refundOrgId());
		validateResponsibleOrganizations(validationRequest);
		account.setAccountName(request.accountName().trim());
		account.setCurrency(StringUtils.hasText(request.currency()) ? request.currency() : "CNY");
		account.setTimezone(StringUtils.hasText(request.timezone()) ? request.timezone() : "Asia/Shanghai");
		account.setResponsibleOrgId(StringUtils.hasText(request.responsibleOrgId()) ? request.responsibleOrgId()
				: account.getAdvertiserOrgId());
		account.setRebateOrgId(request.rebateOrgId());
		account.setRefundOrgId(request.refundOrgId());
		adAccountMapper.updateById(account);
		return account;
	}

	public void closeAdAccount(String id) {
		AdAccount account = requireAdAccount(id);
		scopeService.requireWrite(account.getAdvertiserOrgId());
		account.setStatus("CLOSED");
		adAccountMapper.updateById(account);
	}

	// ── Account Tree ──

	public AccountTreeView buildAccountTree(String rootOrgId) {
		scopeService.requireRead(rootOrgId);
		List<AgencyRelation> relations = agencyRelationMapper
			.selectList(Wrappers.<AgencyRelation>lambdaQuery().eq(AgencyRelation::getStatus, "ACTIVE"));
		Set<String> reachable = collectReachableOrganizationIds(rootOrgId, relations);
		if (!scopeService.isAdmin()) {
			reachable.retainAll(new HashSet<>(scopeService.readableOrganizationIds()));
			reachable.add(rootOrgId);
		}
		List<Organization> organizations = orgMapper
			.selectList(Wrappers.<Organization>lambdaQuery().in(Organization::getId, reachable));
		Map<String, Organization> byId = new HashMap<>();
		organizations.forEach(org -> byId.put(org.getId(), org));
		Organization root = byId.get(rootOrgId);
		if (root == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		List<AdAccount> accounts = adAccountMapper
			.selectList(Wrappers.<AdAccount>lambdaQuery().in(AdAccount::getAdvertiserOrgId, reachable));
		Map<String, List<AdAccount>> accountsByOrg = accounts.stream()
			.collect(java.util.stream.Collectors.groupingBy(AdAccount::getAdvertiserOrgId));
		Map<String, List<String>> childrenByParent = new HashMap<>();
		relations.stream()
			.filter(rel -> reachable.contains(rel.getParentAgencyOrgId()) && reachable.contains(rel.getChildOrgId()))
			.forEach(rel -> childrenByParent.computeIfAbsent(rel.getParentAgencyOrgId(), ignored -> new ArrayList<>())
				.add(rel.getChildOrgId()));
		return buildTreeNode(rootOrgId, byId, childrenByParent, accountsByOrg, new HashSet<>());
	}

	private AccountTreeView buildTreeNode(String orgId, Map<String, Organization> byId,
			Map<String, List<String>> childrenByParent, Map<String, List<AdAccount>> accountsByOrg,
			Set<String> visited) {
		if (!visited.add(orgId)) {
			throw new IllegalStateException("代理关系存在环路");
		}
		Organization org = byId.get(orgId);
		List<AccountTreeView> childViews = childrenByParent.getOrDefault(orgId, List.of())
			.stream()
			.filter(byId::containsKey)
			.map(childId -> buildTreeNode(childId, byId, childrenByParent, accountsByOrg, visited))
			.toList();
		visited.remove(orgId);
		List<AdAccountView> adAccounts = accountsByOrg.getOrDefault(orgId, List.of())
			.stream()
			.map(a -> new AdAccountView(a.getId(), a.getAdvertiserOrgId(), a.getAccountName(), a.getStatus(),
					a.getCurrency()))
			.toList();
		return new AccountTreeView(org.getId(), org.getName(), org.getOrgType(), org.getStatus(), childViews,
				adAccounts);
	}

	// ── helpers ──

	public boolean hasApprovedQualification(String orgId) {
		return qualificationCaseMapper.selectCount(Wrappers.<QualificationCase>lambdaQuery()
			.eq(QualificationCase::getOrgId, orgId)
			.eq(QualificationCase::getStatus, "APPROVED")
			.and(q -> q.isNull(QualificationCase::getExpiredAt)
				.or()
				.gt(QualificationCase::getExpiredAt, java.time.LocalDateTime.now()))) > 0;
	}

	private Organization requireOrgInternal(String orgId) {
		Organization org = orgMapper.selectById(orgId);
		if (org == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		return org;
	}

	private Advertiser requireAdvertiserByOrgId(String orgId) {
		Advertiser advertiser = advertiserMapper
			.selectOne(Wrappers.<Advertiser>lambdaQuery().eq(Advertiser::getOrgId, orgId));
		if (advertiser == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		return advertiser;
	}

	private void validateResponsibleOrganizations(CreateAdAccountRequest req) {
		for (String orgId : List.of(valueOrDefault(req.responsibleOrgId(), req.advertiserOrgId()),
				valueOrDefault(req.rebateOrgId(), req.advertiserOrgId()),
				valueOrDefault(req.refundOrgId(), req.advertiserOrgId()))) {
			if (orgMapper.selectById(orgId) == null) {
				throw new IllegalArgumentException("责任主体不存在");
			}
		}
	}

	private String valueOrDefault(String value, String defaultValue) {
		return StringUtils.hasText(value) ? value : defaultValue;
	}

	private Set<String> collectReachableOrganizationIds(String rootOrgId, List<AgencyRelation> relations) {
		Map<String, List<String>> children = new HashMap<>();
		relations.forEach(rel -> children.computeIfAbsent(rel.getParentAgencyOrgId(), ignored -> new ArrayList<>())
			.add(rel.getChildOrgId()));
		Set<String> result = new HashSet<>();
		List<String> frontier = new ArrayList<>();
		frontier.add(rootOrgId);
		while (!frontier.isEmpty()) {
			String current = frontier.remove(frontier.size() - 1);
			if (result.add(current)) {
				frontier.addAll(children.getOrDefault(current, List.of()));
			}
		}
		return result;
	}

}
