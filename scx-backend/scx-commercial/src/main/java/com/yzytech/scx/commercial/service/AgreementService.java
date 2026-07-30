package com.yzytech.scx.commercial.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.commercial.dto.CreateAgreementInstanceRequest;
import com.yzytech.scx.commercial.dto.CreateAgreementTemplateRequest;
import com.yzytech.scx.commercial.dto.CreateDataAuthorizationRequest;
import com.yzytech.scx.commercial.entity.AgreementInstance;
import com.yzytech.scx.commercial.entity.AgreementTemplate;
import com.yzytech.scx.commercial.entity.DataAuthorization;
import com.yzytech.scx.commercial.entity.Organization;
import com.yzytech.scx.commercial.mapper.AgreementInstanceMapper;
import com.yzytech.scx.commercial.mapper.AgreementTemplateMapper;
import com.yzytech.scx.commercial.mapper.DataAuthorizationMapper;
import com.yzytech.scx.commercial.mapper.OrganizationMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgreementService {

	private final AgreementTemplateMapper templateMapper;

	private final AgreementInstanceMapper instanceMapper;

	private final DataAuthorizationMapper authorizationMapper;

	private final OrganizationMapper organizationMapper;

	private final OrganizationScopeService scopeService;

	@Transactional
	public AgreementTemplate createTemplate(CreateAgreementTemplateRequest request) {
		if (!scopeService.isAdmin()) {
			throw new IllegalArgumentException("资源不存在");
		}
		AgreementTemplate latest = templateMapper.selectOne(Wrappers.<AgreementTemplate>lambdaQuery()
			.eq(AgreementTemplate::getTemplateType, request.templateType())
			.orderByDesc(AgreementTemplate::getVersion)
			.last("LIMIT 1"));
		AgreementTemplate template = new AgreementTemplate();
		template.setTemplateType(request.templateType());
		template.setName(request.name());
		template.setContent(request.content());
		template.setVersion(latest == null ? 1 : latest.getVersion() + 1);
		template.setStatus("DRAFT");
		templateMapper.insert(template);
		return template;
	}

	@Transactional
	public AgreementTemplate activateTemplate(String id) {
		if (!scopeService.isAdmin()) {
			throw new IllegalArgumentException("资源不存在");
		}
		AgreementTemplate template = requireTemplate(id);
		if (!"DRAFT".equals(template.getStatus())) {
			throw new IllegalArgumentException("只有草稿模板可启用");
		}
		templateMapper.update(null,
				Wrappers.<AgreementTemplate>lambdaUpdate()
					.eq(AgreementTemplate::getTemplateType, template.getTemplateType())
					.eq(AgreementTemplate::getStatus, "ACTIVE")
					.set(AgreementTemplate::getStatus, "ARCHIVED"));
		template.setStatus("ACTIVE");
		templateMapper.updateById(template);
		return template;
	}

	@Transactional
	public AgreementTemplate updateTemplate(String id, CreateAgreementTemplateRequest request) {
		if (!scopeService.isAdmin()) {
			throw new IllegalArgumentException("资源不存在");
		}
		AgreementTemplate template = requireTemplate(id);
		if (!"DRAFT".equals(template.getStatus())) {
			throw new IllegalArgumentException("只有草稿模板可编辑");
		}
		if (!template.getTemplateType().equals(request.templateType())) {
			throw new IllegalArgumentException("模板类型不可修改，请新建模板版本");
		}
		template.setName(request.name());
		template.setContent(request.content());
		templateMapper.updateById(template);
		return template;
	}

	@Transactional
	public void archiveTemplate(String id) {
		if (!scopeService.isAdmin()) {
			throw new IllegalArgumentException("资源不存在");
		}
		AgreementTemplate template = requireTemplate(id);
		if ("ACTIVE".equals(template.getStatus())) {
			throw new IllegalArgumentException("启用中的模板不可删除，请先启用替代版本");
		}
		template.setStatus("ARCHIVED");
		templateMapper.updateById(template);
	}

	@Transactional
	public AgreementInstance createInstance(CreateAgreementInstanceRequest request) {
		scopeService.requireWrite(request.organizationId());
		AgreementTemplate template = requireTemplate(request.templateId());
		if (!"ACTIVE".equals(template.getStatus())) {
			throw new IllegalArgumentException("协议模板未启用");
		}
		if (request.expiredAt() != null && !request.expiredAt().isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("协议失效时间必须晚于当前时间");
		}
		AgreementInstance instance = new AgreementInstance();
		instance.setTemplateId(template.getId());
		instance.setTemplateVersion(template.getVersion());
		instance.setOrgId(request.organizationId());
		instance.setStatus("DRAFT");
		instance.setExpiredAt(request.expiredAt());
		instanceMapper.insert(instance);
		return instance;
	}

	@Transactional
	public AgreementInstance updateInstance(String id, CreateAgreementInstanceRequest request) {
		AgreementInstance instance = requireInstance(id, true);
		if (!"DRAFT".equals(instance.getStatus())) {
			throw new IllegalArgumentException("只有草稿协议可编辑");
		}
		if (!instance.getOrgId().equals(request.organizationId())) {
			throw new IllegalArgumentException("协议所属组织不可修改");
		}
		AgreementTemplate template = requireTemplate(request.templateId());
		if (!"ACTIVE".equals(template.getStatus())) {
			throw new IllegalArgumentException("协议模板未启用");
		}
		if (request.expiredAt() != null && !request.expiredAt().isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("协议失效时间必须晚于当前时间");
		}
		instance.setTemplateId(template.getId());
		instance.setTemplateVersion(template.getVersion());
		instance.setExpiredAt(request.expiredAt());
		instanceMapper.updateById(instance);
		return instance;
	}

	@Transactional
	public void cancelInstance(String id) {
		AgreementInstance instance = requireInstance(id, true);
		if ("SIGNED".equals(instance.getStatus())) {
			terminate(id);
			return;
		}
		if (!"DRAFT".equals(instance.getStatus())) {
			throw new IllegalArgumentException("当前协议不可作废");
		}
		instance.setStatus("CANCELLED");
		instanceMapper.updateById(instance);
	}

	@Transactional
	public AgreementInstance sign(String id) {
		AgreementInstance instance = requireInstance(id, true);
		if (!"DRAFT".equals(instance.getStatus())) {
			throw new IllegalArgumentException("只有草稿协议可签署");
		}
		if (instance.getExpiredAt() != null && !instance.getExpiredAt().isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("协议已过期");
		}
		instance.setStatus("SIGNED");
		instance.setSignedAt(LocalDateTime.now());
		instanceMapper.updateById(instance);
		return instance;
	}

	@Transactional
	public AgreementInstance terminate(String id) {
		AgreementInstance instance = requireInstance(id, true);
		if (!"SIGNED".equals(instance.getStatus())) {
			throw new IllegalArgumentException("只有已签署协议可终止");
		}
		instance.setStatus("TERMINATED");
		instanceMapper.updateById(instance);
		return instance;
	}

	public Page<AgreementInstance> pageInstances(Page<AgreementInstance> page, String organizationId) {
		if (organizationId != null) {
			scopeService.requireRead(organizationId);
		}
		List<String> readableIds = scopeService.readableOrganizationIds();
		if (!scopeService.isAdmin() && readableIds.isEmpty()) {
			page.setRecords(List.of());
			page.setTotal(0);
			return page;
		}
		return instanceMapper.selectPage(page,
				Wrappers.<AgreementInstance>lambdaQuery()
					.in(!scopeService.isAdmin(), AgreementInstance::getOrgId, readableIds)
					.eq(organizationId != null, AgreementInstance::getOrgId, organizationId)
					.orderByDesc(AgreementInstance::getCreateTime)
					.orderByDesc(AgreementInstance::getId));
	}

	public Page<AgreementTemplate> pageTemplates(Page<AgreementTemplate> page, String templateType, String status) {
		return templateMapper.selectPage(page,
				Wrappers.<AgreementTemplate>lambdaQuery()
					.eq(templateType != null, AgreementTemplate::getTemplateType, templateType)
					.eq(status != null, AgreementTemplate::getStatus, status)
					.orderByDesc(AgreementTemplate::getCreateTime));
	}

	public AgreementInstance getInstance(String id) {
		return requireInstance(id, false);
	}

	public Page<DataAuthorization> pageAuthorizations(Page<DataAuthorization> page, String organizationId,
			String status) {
		if (organizationId != null) {
			scopeService.requireRead(organizationId);
		}
		List<String> readableIds = scopeService.readableOrganizationIds();
		if (!scopeService.isAdmin() && readableIds.isEmpty()) {
			page.setRecords(List.of());
			page.setTotal(0);
			return page;
		}
		return authorizationMapper.selectPage(page,
				Wrappers.<DataAuthorization>lambdaQuery()
					.and(!scopeService.isAdmin(),
							q -> q.in(DataAuthorization::getGrantorOrgId, readableIds)
								.or()
								.in(DataAuthorization::getGranteeOrgId, readableIds))
					.and(organizationId != null,
							q -> q.eq(DataAuthorization::getGrantorOrgId, organizationId)
								.or()
								.eq(DataAuthorization::getGranteeOrgId, organizationId))
					.eq(status != null, DataAuthorization::getStatus, status)
					.orderByDesc(DataAuthorization::getCreateTime));
	}

	@Transactional
	public DataAuthorization createAuthorization(CreateDataAuthorizationRequest request) {
		scopeService.requireWrite(request.grantorOrganizationId());
		requireActiveOrganization(request.granteeOrganizationId());
		if (request.endDate() != null && request.startDate() != null
				&& request.endDate().isBefore(request.startDate())) {
			throw new IllegalArgumentException("授权结束日期不能早于开始日期");
		}
		DataAuthorization authorization = new DataAuthorization();
		authorization.setGrantorOrgId(request.grantorOrganizationId());
		authorization.setGranteeOrgId(request.granteeOrganizationId());
		authorization.setAuthorizationType(request.authorizationType());
		authorization.setScope(request.scope());
		authorization.setStartDate(request.startDate());
		authorization.setEndDate(request.endDate());
		authorization.setStatus("ACTIVE");
		authorizationMapper.insert(authorization);
		return authorization;
	}

	@Transactional
	public DataAuthorization revokeAuthorization(String id) {
		DataAuthorization authorization = authorizationMapper.selectById(id);
		if (authorization == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		scopeService.requireWrite(authorization.getGrantorOrgId());
		if (!"ACTIVE".equals(authorization.getStatus())) {
			throw new IllegalArgumentException("授权已失效");
		}
		authorization.setStatus("REVOKED");
		authorizationMapper.updateById(authorization);
		return authorization;
	}

	@Transactional
	public DataAuthorization updateAuthorization(String id, CreateDataAuthorizationRequest request) {
		DataAuthorization authorization = authorizationMapper.selectById(id);
		if (authorization == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		scopeService.requireWrite(authorization.getGrantorOrgId());
		if (!"ACTIVE".equals(authorization.getStatus())) {
			throw new IllegalArgumentException("只有生效中的授权可编辑");
		}
		if (!authorization.getGrantorOrgId().equals(request.grantorOrganizationId())
				|| !authorization.getGranteeOrgId().equals(request.granteeOrganizationId())) {
			throw new IllegalArgumentException("授权双方不可修改，请撤销后重新授权");
		}
		if (request.endDate() != null && request.startDate() != null
				&& request.endDate().isBefore(request.startDate())) {
			throw new IllegalArgumentException("授权结束日期不能早于开始日期");
		}
		authorization.setAuthorizationType(request.authorizationType());
		authorization.setScope(request.scope());
		authorization.setStartDate(request.startDate());
		authorization.setEndDate(request.endDate());
		authorizationMapper.updateById(authorization);
		return authorization;
	}

	public boolean hasActiveAgreement(String organizationId) {
		return instanceMapper.selectCount(Wrappers.<AgreementInstance>lambdaQuery()
			.eq(AgreementInstance::getOrgId, organizationId)
			.eq(AgreementInstance::getStatus, "SIGNED")
			.and(q -> q.isNull(AgreementInstance::getExpiredAt).or().apply("expired_at > NOW()"))) > 0;
	}

	public boolean hasActiveAuthorization(String grantorOrganizationId, String granteeOrganizationId) {
		if (grantorOrganizationId.equals(granteeOrganizationId)) {
			return true;
		}
		LocalDate today = LocalDate.now();
		return authorizationMapper.selectCount(Wrappers.<DataAuthorization>lambdaQuery()
			.eq(DataAuthorization::getGrantorOrgId, grantorOrganizationId)
			.eq(DataAuthorization::getGranteeOrgId, granteeOrganizationId)
			.eq(DataAuthorization::getStatus, "ACTIVE")
			.and(q -> q.isNull(DataAuthorization::getStartDate).or().le(DataAuthorization::getStartDate, today))
			.and(q -> q.isNull(DataAuthorization::getEndDate).or().ge(DataAuthorization::getEndDate, today))) > 0;
	}

	private AgreementTemplate requireTemplate(String id) {
		AgreementTemplate template = templateMapper.selectById(id);
		if (template == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		return template;
	}

	private AgreementInstance requireInstance(String id, boolean write) {
		AgreementInstance instance = instanceMapper.selectById(id);
		if (instance == null) {
			throw new IllegalArgumentException("资源不存在");
		}
		if (write) {
			scopeService.requireWrite(instance.getOrgId());
		}
		else {
			scopeService.requireRead(instance.getOrgId());
		}
		return instance;
	}

	private void requireActiveOrganization(String id) {
		Organization organization = organizationMapper.selectById(id);
		if (organization == null || !"ACTIVE".equals(organization.getStatus())) {
			throw new IllegalArgumentException("资源不存在");
		}
	}

}
