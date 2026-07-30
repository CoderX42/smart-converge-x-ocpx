package com.yzytech.scx.commercial.service;

import com.yzytech.scx.commercial.entity.*;
import com.yzytech.scx.commercial.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QualificationServiceTest {

	private final QualificationCaseMapper caseMapper = mock(QualificationCaseMapper.class);

	private final QualificationDocumentMapper docMapper = mock(QualificationDocumentMapper.class);

	private final QualificationReviewLogMapper reviewLogMapper = mock(QualificationReviewLogMapper.class);

	private final OrganizationMapper orgMapper = mock(OrganizationMapper.class);

	private final OrganizationScopeService scopeService = mock(OrganizationScopeService.class);

	private final QualificationService service = new QualificationService(caseMapper, docMapper, reviewLogMapper,
			orgMapper, scopeService);

	@BeforeEach
	void setUp() {
		reset(caseMapper, docMapper, reviewLogMapper, orgMapper, scopeService);
		when(scopeService.canRead(any())).thenReturn(true);
	}

	@Test
	void createCaseSucceedsWhenOrgExists() {
		Organization org = new Organization();
		org.setId("org-1");
		when(orgMapper.selectById("org-1")).thenReturn(org);

		QualificationCase c = service.createCase("org-1", "ADVERTISER_QUALIFICATION");

		assertThat(c.getStatus()).isEqualTo("DRAFT");
		assertThat(c.getCaseType()).isEqualTo("ADVERTISER_QUALIFICATION");
		// state assertion is sufficient
		verify(reviewLogMapper).insert(any(QualificationReviewLog.class));
	}

	@Test
	void createCaseFailsWhenOrgNotFound() {
		when(orgMapper.selectById("bad-org")).thenReturn(null);
		assertThatThrownBy(() -> service.createCase("bad-org", "ADVERTISER_QUALIFICATION"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不存在");
	}

	@Test
	void submitCaseChangesStatusToSubmitted() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setStatus("DRAFT");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		service.submitCase("case-1");

		assertThat(c.getStatus()).isEqualTo("SUBMITTED");
		assertThat(c.getSubmittedAt()).isNotNull();
		verify(caseMapper).updateById(c);
	}

	@Test
	void submitCaseFailsWhenAlreadySubmitted() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setStatus("SUBMITTED");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		assertThatThrownBy(() -> service.submitCase("case-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不可提交");
	}

	@Test
	void reviewCaseApproves() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setStatus("SUBMITTED");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		service.reviewCase("case-1", "APPROVE", "通过");

		assertThat(c.getStatus()).isEqualTo("APPROVED");
		assertThat(c.getReviewedAt()).isNotNull();
		verify(reviewLogMapper).insert(any(QualificationReviewLog.class));
	}

	@Test
	void reviewCaseRejects() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setStatus("SUBMITTED");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		service.reviewCase("case-1", "REJECT", "不通过");

		assertThat(c.getStatus()).isEqualTo("REJECTED");
	}

	@Test
	void reviewCaseRequiresSupplement() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setStatus("MANUAL_REVIEW");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		service.reviewCase("case-1", "REQUIRE_SUPPLEMENT", "请补充营业执照");

		assertThat(c.getStatus()).isEqualTo("SUPPLEMENT_REQUIRED");
	}

	@Test
	void reviewCaseFailsOnInvalidDecision() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setStatus("SUBMITTED");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		assertThatThrownBy(() -> service.reviewCase("case-1", "INVALID", ""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("无效审核决定");
	}

	@Test
	void suspendCaseOnlyForApproved() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setStatus("APPROVED");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		service.suspendCase("case-1", "违规");

		assertThat(c.getStatus()).isEqualTo("SUSPENDED");
	}

	@Test
	void suspendCaseFailsForNonApproved() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setStatus("DRAFT");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		assertThatThrownBy(() -> service.suspendCase("case-1", "reason")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("已通过");
	}

	@Test
	void addDocumentSucceeds() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		QualificationDocument doc = service.addDocument("case-1", "BUSINESS_LICENSE", "营业执照", "/files/license.pdf");

		assertThat(doc.getCaseId()).isEqualTo("case-1");
		assertThat(doc.getStatus()).isEqualTo("ACTIVE");
		// state assertion is sufficient
	}

	@Test
	void cancellingDraftKeepsAnAuditLog() {
		QualificationCase c = new QualificationCase();
		c.setId("case-1");
		c.setOrgId("org-1");
		c.setStatus("DRAFT");
		when(caseMapper.selectById("case-1")).thenReturn(c);

		service.cancelCase("case-1");

		assertThat(c.getStatus()).isEqualTo("CANCELLED");
		verify(caseMapper).updateById(c);
		verify(reviewLogMapper).insert(any(QualificationReviewLog.class));
	}

}
