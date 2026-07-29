package com.yzytech.scx.commercial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yzytech.scx.commercial.dto.CreateAgreementInstanceRequest;
import com.yzytech.scx.commercial.dto.CreateAgreementTemplateRequest;
import com.yzytech.scx.commercial.entity.AgreementInstance;
import com.yzytech.scx.commercial.entity.AgreementTemplate;
import com.yzytech.scx.commercial.mapper.AgreementInstanceMapper;
import com.yzytech.scx.commercial.mapper.AgreementTemplateMapper;
import com.yzytech.scx.commercial.mapper.DataAuthorizationMapper;
import com.yzytech.scx.commercial.mapper.OrganizationMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AgreementServiceTest {

	private final AgreementTemplateMapper templateMapper = mock(AgreementTemplateMapper.class);

	private final AgreementInstanceMapper instanceMapper = mock(AgreementInstanceMapper.class);

	private final DataAuthorizationMapper authorizationMapper = mock(DataAuthorizationMapper.class);

	private final OrganizationMapper organizationMapper = mock(OrganizationMapper.class);

	private final OrganizationScopeService scopeService = mock(OrganizationScopeService.class);

	private final AgreementService service = new AgreementService(templateMapper, instanceMapper, authorizationMapper,
			organizationMapper, scopeService);

	@Test
	void templateRevisionAlwaysCreatesANewVersion() {
		when(scopeService.isAdmin()).thenReturn(true);
		AgreementTemplate current = new AgreementTemplate();
		current.setVersion(3);
		when(templateMapper.selectOne(any())).thenReturn(current);

		AgreementTemplate created = service.createTemplate(new CreateAgreementTemplateRequest("MEDIA", "媒体协议", "v4"));

		assertThat(created.getVersion()).isEqualTo(4);
		assertThat(created.getStatus()).isEqualTo("DRAFT");
	}

	@Test
	void agreementInstancePinsTemplateVersion() {
		AgreementTemplate template = new AgreementTemplate();
		template.setId("tpl-1");
		template.setVersion(7);
		template.setStatus("ACTIVE");
		when(templateMapper.selectById("tpl-1")).thenReturn(template);

		AgreementInstance instance = service
			.createInstance(new CreateAgreementInstanceRequest("tpl-1", "org-1", LocalDateTime.now().plusDays(1)));

		assertThat(instance.getTemplateVersion()).isEqualTo(7);
		assertThat(instance.getStatus()).isEqualTo("DRAFT");
	}

	@Test
	void expiredAgreementCannotBeSigned() {
		AgreementInstance instance = new AgreementInstance();
		instance.setId("agr-1");
		instance.setOrgId("org-1");
		instance.setStatus("DRAFT");
		instance.setExpiredAt(LocalDateTime.now().minusMinutes(1));
		when(instanceMapper.selectById("agr-1")).thenReturn(instance);

		assertThatThrownBy(() -> service.sign("agr-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("过期");
	}

}
