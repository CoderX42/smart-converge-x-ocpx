package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.dto.AppLinkRequests;
import com.yzytech.scx.ocpx.admin.dto.AppLinkViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLinkChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxDeductionRule;
import com.yzytech.scx.ocpx.admin.entity.OcpxIntegrationDocument;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxIntegrationDocumentMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxAppLinkServiceTest {

	@BeforeAll
	static void initializeTableMetadata() {
		for (Class<?> entity : new Class<?>[] { OcpxAppLink.class, OcpxProduct.class, OcpxMonitorLink.class,
				OcpxDeductionRule.class }) {
			TableInfoHelper
				.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), entity.getSimpleName()), entity);
		}
	}

	private final OcpxAppLinkMapper appLinkMapper = mock(OcpxAppLinkMapper.class);

	private final OcpxAppLinkChangeAuditMapper auditMapper = mock(OcpxAppLinkChangeAuditMapper.class);

	private final OcpxProductMapper productMapper = mock(OcpxProductMapper.class);

	private final OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);

	private final OcpxIntegrationDocumentMapper documentMapper = mock(OcpxIntegrationDocumentMapper.class);

	private final OcpxMonitorLinkMapper monitorMapper = mock(OcpxMonitorLinkMapper.class);

	private final OcpxDeductionRuleMapper deductionMapper = mock(OcpxDeductionRuleMapper.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxConfigChangeService configChangeService = mock(OcpxConfigChangeService.class);

	private OcpxAppLinkService service;

	@BeforeEach
	void setUp() {
		service = new OcpxAppLinkService(appLinkMapper, auditMapper, productMapper, advertiserMapper, documentMapper,
				monitorMapper, deductionMapper, accessScope, configChangeService, new OcpxUrlPolicy(),
				new ObjectMapper());
		when(appLinkMapper.selectCount(any())).thenReturn(0L);
		when(monitorMapper.selectCount(any())).thenReturn(0L);
		when(deductionMapper.selectCount(any())).thenReturn(0L);
	}

	@Test
	void createsStructuredLinkWithoutReturningOrAuditingTheFullUrl() {
		when(productMapper.selectById("product-1")).thenReturn(product("advertiser-1"));
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser("advertiser-1"));
		when(documentMapper.selectById("document-1")).thenReturn(document("advertiser-1"));
		when(appLinkMapper.insert(any(OcpxAppLink.class))).thenAnswer(invocation -> {
			((OcpxAppLink) invocation.getArgument(0)).setId("app-link-1");
			return 1;
		});
		AppLinkRequests.Create request = new AppLinkRequests.Create("product-1", "document-1", "ANDROID", "Android 主链路",
				"HTTPS://8.8.4.4:443/click?clickid=__CLICKID__", true, "https://8.8.4.4/open",
				"https://8.8.4.4/download", "primary");

		AppLinkViews.Detail result;
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			result = service.create("app-link-request-0001", request);
		}

		assertThat(result.id()).isEqualTo("app-link-1");
		assertThat(result.status()).isEqualTo("DISABLED");
		assertThat(result.templateMasked()).isEqualTo("https://8.8.4.4/…?query=•••");
		assertThat(result.toString()).doesNotContain("/click", "/open", "/download");
		assertThat(result.macroSet()).containsExactly("__CLICKID__");
		assertThat(Arrays.stream(AppLinkViews.Detail.class.getRecordComponents()).map(component -> component.getName()))
			.doesNotContain("linkUrl", "linkUrlTemplate", "deeplinkUrl", "downloadUrl", "objectKey");
		assertThat(
				Arrays.stream(AppLinkRequests.Create.class.getRecordComponents()).map(component -> component.getName()))
			.doesNotContain("status");
		assertThat(
				Arrays.stream(AppLinkRequests.Update.class.getRecordComponents()).map(component -> component.getName()))
			.doesNotContain("status");
		ArgumentCaptor<OcpxAppLink> stored = ArgumentCaptor.forClass(OcpxAppLink.class);
		verify(appLinkMapper).insert(stored.capture());
		assertThat(stored.getValue().getLinkUrlTemplate()).isEqualTo("https://8.8.4.4/click?clickid=__CLICKID__");
		assertThat(stored.getValue().getTemplateFingerprint()).hasSize(64);
		ArgumentCaptor<OcpxAppLinkChangeAudit> audit = ArgumentCaptor.forClass(OcpxAppLinkChangeAudit.class);
		verify(auditMapper).insert(audit.capture());
		assertThat(audit.getValue().getChangeSummary()).doesNotContain("https://", "__CLICKID__");
		assertThat(audit.getValue().getTemplateHost()).isEqualTo("8.8.4.4");
	}

	@Test
	void urlValidationIsSyntacticOnlyAndBlocksPrivateTargets() {
		AppLinkViews.ValidationReport publicResult = service
			.validateUrl(new AppLinkRequests.ValidateUrl("TEMPLATE", "ANDROID", "https://8.8.4.4/click"));
		AppLinkViews.ValidationReport privateResult = service
			.validateUrl(new AppLinkRequests.ValidateUrl("DOWNLOAD", "ANDROID", "http://127.0.0.1/package"));

		assertThat(publicResult.status()).isEqualTo("PASS");
		assertThat(publicResult.networkCalled()).isFalse();
		assertThat(privateResult.status()).isEqualTo("BLOCK");
		assertThat(privateResult.networkCalled()).isFalse();
		assertThat(privateResult.issues()).contains("URL_HOST_NOT_PUBLIC");
	}

	@Test
	void rejectsStaleUpdateBeforeChangingStoredUrls() {
		OcpxAppLink link = readyLink(5L);
		when(appLinkMapper.selectOne(any())).thenReturn(link);

		assertThatThrownBy(() -> service.update("app-link-1",
				new AppLinkRequests.Update("product-1", "document-1", "ANDROID", "Android 主链路", null, true, null, null,
						false, false, null, 4L)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("OCPX_VERSION_CONFLICT");
	}

	@Test
	void legacyMappingNeverAllowsAProductFromAnotherAdvertiser() {
		OcpxAppLink legacy = readyLink(0L);
		legacy.setProductId(null);
		legacy.setMappingStatus("LEGACY_PENDING_PRODUCT");
		when(appLinkMapper.selectOne(any())).thenReturn(legacy);
		when(productMapper.selectById("product-1")).thenReturn(product("advertiser-other"));
		when(advertiserMapper.selectById("advertiser-other")).thenReturn(advertiser("advertiser-other"));
		when(documentMapper.selectById("document-1")).thenReturn(document("advertiser-other"));

		assertThatThrownBy(() -> service.completeLegacyMapping("app-link-1",
				new AppLinkRequests.CompleteLegacyMapping("product-1", "document-1", "ANDROID", "人工复核链接",
						"https://8.8.4.4/click", false, null, null, "reviewed", 0L)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Product 不属于存量链接原广告主");
	}

	@Test
	void hidesAExistingLinkWhenAdvertiserScopeDoesNotAllowIt() {
		when(appLinkMapper.selectById("app-link-1")).thenReturn(readyLink(1L));
		doThrow(new BootBusinessException(403, "对象不存在或无权访问")).when(accessScope)
			.require(OcpxAccessScope.ScopeType.ADVERTISER, "advertiser-1", OcpxAccessScope.PermissionLevel.VIEW);

		assertThatThrownBy(() -> service.detail("app-link-1")).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不存在或无权访问");
	}

	@Test
	void blocksArchiveWhileAReferenceStillExists() {
		when(appLinkMapper.selectOne(any())).thenReturn(readyLink(2L));
		when(monitorMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.archive("app-link-1", new AppLinkRequests.VersionedAction(2L, "obsolete")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("OCPX_OBJECT_IN_USE");
	}

	private OcpxAppLink readyLink(long version) {
		OcpxUrlPolicy.Result checked = new OcpxUrlPolicy().validate("https://8.8.4.4/click");
		OcpxAppLink link = new OcpxAppLink();
		link.setId("app-link-1");
		link.setAdvertiserId("advertiser-1");
		link.setProductId("product-1");
		link.setDocumentId("document-1");
		link.setDocumentVersion(1L);
		link.setLinkName("Android 主链路");
		link.setLinkType("ANDROID");
		link.setLinkUrlTemplate(checked.normalizedUrl());
		link.setTemplateHost(checked.host());
		link.setTemplateFingerprint(checked.fingerprint());
		link.setTemplateMacroSet("[]");
		link.setRedirectEnabled(false);
		link.setStatus("ACTIVE");
		link.setMappingStatus("READY");
		link.setConfigVersion(version);
		return link;
	}

	private OcpxProduct product(String advertiserId) {
		OcpxProduct product = new OcpxProduct();
		product.setId("product-1");
		product.setAdvertiserId(advertiserId);
		product.setCode("PRODUCT_1");
		product.setName("测试产品");
		product.setStatus("ACTIVE");
		product.setConfigVersion(1L);
		return product;
	}

	private OcpxAdvertiser advertiser(String id) {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId(id);
		advertiser.setAdvertiserCode("ADV_1");
		advertiser.setAdvertiserName("测试广告主");
		advertiser.setStatus("ACTIVE");
		advertiser.setCurrentDocumentId("document-1");
		return advertiser;
	}

	private OcpxIntegrationDocument document(String advertiserId) {
		OcpxIntegrationDocument document = new OcpxIntegrationDocument();
		document.setId("document-1");
		document.setOwnerType("ADVERTISER");
		document.setOwnerId(advertiserId);
		document.setDocumentVersion(1L);
		document.setStatus("ACTIVE");
		document.setScanStatus("CLEAN");
		return document;
	}

}
