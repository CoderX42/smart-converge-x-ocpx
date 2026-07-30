package com.yzytech.scx.commercial.service;

import com.yzytech.scx.commercial.dto.AccountTreeView;
import com.yzytech.scx.commercial.dto.CreateAgencyRelationRequest;
import java.time.LocalDate;
import com.yzytech.scx.commercial.entity.*;
import com.yzytech.scx.commercial.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommercialServiceTest {

	private final OrganizationMapper orgMapper = mock(OrganizationMapper.class);

	private final AdvertiserMapper advertiserMapper = mock(AdvertiserMapper.class);

	private final AgencyRelationMapper agencyRelationMapper = mock(AgencyRelationMapper.class);

	private final AdAccountMapper adAccountMapper = mock(AdAccountMapper.class);

	private final QualificationCaseMapper qualificationCaseMapper = mock(QualificationCaseMapper.class);

	private final OrganizationScopeService scopeService = mock(OrganizationScopeService.class);

	private final CommercialService service = new CommercialService(orgMapper, advertiserMapper, agencyRelationMapper,
			adAccountMapper, qualificationCaseMapper, scopeService);

	@BeforeEach
	void setUp() {
		reset(orgMapper, advertiserMapper, agencyRelationMapper, adAccountMapper, qualificationCaseMapper,
				scopeService);
		when(scopeService.isAdmin()).thenReturn(true);
		when(scopeService.canRead(anyString())).thenReturn(true);
	}

	// ── Organization ──

	@Test
	void createOrganizationSucceedsWhenNameUnique() {
		when(orgMapper.selectCount(any())).thenReturn(0L);
		Organization org = service.createOrganization("测试企业", "ENTERPRISE", null, "张三", "13800138000", "a@b.com");
		assertThat(org.getName()).isEqualTo("测试企业");
		assertThat(org.getStatus()).isEqualTo("ACTIVE");
	}

	@Test
	void createOrganizationFailsWhenNameDuplicate() {
		when(orgMapper.selectCount(any())).thenReturn(1L);
		assertThatThrownBy(() -> service.createOrganization("重复", "ENTERPRISE", null, null, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("已存在");
	}

	// ── Agency ──

	@Test
	void createAgencyRelationFailsWhenSelfReferencing() {
		Organization parent = new Organization();
		parent.setId("org-1");
		parent.setOrgType("AGENCY");
		when(orgMapper.selectById("org-1")).thenReturn(parent);

		assertThatThrownBy(() -> service
			.createAgencyRelation(new CreateAgencyRelationRequest("org-1", "org-1", null, null, null, null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("自身");
	}

	@Test
	void createAgencyRelationFailsWhenParentNotAgency() {
		Organization parent = new Organization();
		parent.setId("org-1");
		parent.setOrgType("DIRECT_ADVERTISER");
		Organization child = new Organization();
		child.setId("org-2");
		child.setOrgType("MANAGED_ADVERTISER");
		when(orgMapper.selectById("org-1")).thenReturn(parent);
		when(orgMapper.selectById("org-2")).thenReturn(child);

		assertThatThrownBy(() -> service
			.createAgencyRelation(new CreateAgencyRelationRequest("org-1", "org-2", null, null, null, null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("代理商");
	}

	@Test
	void revokeAgencyRelationSetsStatusRevoked() {
		AgencyRelation rel = new AgencyRelation();
		rel.setId("rel-1");
		rel.setStatus("ACTIVE");
		when(agencyRelationMapper.selectById("rel-1")).thenReturn(rel);

		service.revokeAgencyRelation("rel-1");

		assertThat(rel.getStatus()).isEqualTo("REVOKED");
	}

	@Test
	void updateAgencyRelationChangesTermsWithoutRevokingIt() {
		AgencyRelation rel = new AgencyRelation();
		rel.setId("rel-1");
		rel.setParentAgencyOrgId("agency-1");
		rel.setChildOrgId("advertiser-1");
		rel.setStatus("ACTIVE");
		when(agencyRelationMapper.selectById("rel-1")).thenReturn(rel);
		LocalDate endDate = LocalDate.now().plusMonths(1);

		AgencyRelation updated = service.updateAgencyRelation("rel-1", new CreateAgencyRelationRequest("agency-1",
				"advertiser-1", "MANAGED", "[\"REPORT\"]", LocalDate.now(), endDate));

		assertThat(updated.getStatus()).isEqualTo("ACTIVE");
		assertThat(updated.getRelationType()).isEqualTo("MANAGED");
		assertThat(updated.getEndDate()).isEqualTo(endDate);
		verify(agencyRelationMapper).updateById(rel);
	}

	// ── Ad Account ──

	@Test
	void updateAdAccountToActiveFailsWhenAdvertiserNotActive() {
		AdAccount acct = new AdAccount();
		acct.setId("acct-1");
		acct.setAdvertiserOrgId("adv-1");
		acct.setStatus("DRAFT");
		Advertiser adv = new Advertiser();
		adv.setId("adv-1");
		adv.setStatus("DRAFT");
		when(adAccountMapper.selectById("acct-1")).thenReturn(acct);
		when(advertiserMapper.selectOne(any())).thenReturn(adv);

		assertThatThrownBy(() -> service.updateAdAccountStatus("acct-1", "ACTIVE"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("未激活");
	}

	@Test
	void closedAdAccountCannotBeReactivated() {
		AdAccount account = new AdAccount();
		account.setId("acct-1");
		account.setAdvertiserOrgId("adv-1");
		account.setStatus("CLOSED");
		when(adAccountMapper.selectById("acct-1")).thenReturn(account);

		assertThatThrownBy(() -> service.updateAdAccountStatus("acct-1", "ACTIVE"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不可重新启用");
	}

	// ── Account Tree ──

	@Test
	void buildAccountTreeReturnsRootWithoutChildren() {
		Organization root = new Organization();
		root.setId("root");
		root.setName("根组织");
		root.setOrgType("ENTERPRISE");
		root.setStatus("ACTIVE");
		when(orgMapper.selectById("root")).thenReturn(root);
		when(orgMapper.selectList(any())).thenReturn(java.util.List.of(root));
		when(agencyRelationMapper.selectList(any())).thenReturn(java.util.List.of());
		when(adAccountMapper.selectList(any())).thenReturn(java.util.List.of());

		AccountTreeView tree = service.buildAccountTree("root");

		assertThat(tree.orgId()).isEqualTo("root");
		assertThat(tree.children()).isEmpty();
	}

}
