package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.adx.dto.AppLinkRequests;
import com.yzytech.scx.adx.dto.AppLinkViews;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxAppLink;
import com.yzytech.scx.adx.entity.AdxAppLinkChangeAudit;
import com.yzytech.scx.adx.entity.AdxDeductionRule;
import com.yzytech.scx.adx.entity.AdxIntegrationDocument;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.entity.AdxProduct;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxAppLinkChangeAuditMapper;
import com.yzytech.scx.adx.mapper.AdxAppLinkMapper;
import com.yzytech.scx.adx.mapper.AdxDeductionRuleMapper;
import com.yzytech.scx.adx.mapper.AdxIntegrationDocumentMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import com.yzytech.scx.adx.mapper.AdxProductMapper;
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

class AdxAppLinkServiceTest {

	@BeforeAll
	static void initializeTableMetadata() {
		for (Class<?> entity : new Class<?>[] { AdxAppLink.class, AdxProduct.class, AdxMonitorLink.class,
				AdxDeductionRule.class }) {
			TableInfoHelper
				.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), entity.getSimpleName()), entity);
		}
	}

	private final AdxAppLinkMapper appLinkMapper = mock(AdxAppLinkMapper.class);

	private final AdxAppLinkChangeAuditMapper auditMapper = mock(AdxAppLinkChangeAuditMapper.class);

	private final AdxProductMapper productMapper = mock(AdxProductMapper.class);

	private final AdxAdvertiserMapper advertiserMapper = mock(AdxAdvertiserMapper.class);

	private final AdxIntegrationDocumentMapper documentMapper = mock(AdxIntegrationDocumentMapper.class);

	private final AdxMonitorLinkMapper monitorMapper = mock(AdxMonitorLinkMapper.class);

	private final AdxDeductionRuleMapper deductionMapper = mock(AdxDeductionRuleMapper.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxConfigChangeService configChangeService = mock(AdxConfigChangeService.class);

	private AdxAppLinkService service;

	@BeforeEach
	void setUp() {
		service = new AdxAppLinkService(appLinkMapper, auditMapper, productMapper, advertiserMapper, documentMapper,
				monitorMapper, deductionMapper, accessScope, configChangeService, new AdxUrlPolicy(),
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
		when(appLinkMapper.insert(any(AdxAppLink.class))).thenAnswer(invocation -> {
			((AdxAppLink) invocation.getArgument(0)).setId("app-link-1");
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
		ArgumentCaptor<AdxAppLink> stored = ArgumentCaptor.forClass(AdxAppLink.class);
		verify(appLinkMapper).insert(stored.capture());
		assertThat(stored.getValue().getLinkUrlTemplate()).isEqualTo("https://8.8.4.4/click?clickid=__CLICKID__");
		assertThat(stored.getValue().getTemplateFingerprint()).hasSize(64);
		ArgumentCaptor<AdxAppLinkChangeAudit> audit = ArgumentCaptor.forClass(AdxAppLinkChangeAudit.class);
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
		AdxAppLink link = readyLink(5L);
		when(appLinkMapper.selectOne(any())).thenReturn(link);

		assertThatThrownBy(() -> service.update("app-link-1",
				new AppLinkRequests.Update("product-1", "document-1", "ANDROID", "Android 主链路", null, true, null, null,
						false, false, null, 4L)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("ADX_VERSION_CONFLICT");
	}

	@Test
	void legacyMappingNeverAllowsAProductFromAnotherAdvertiser() {
		AdxAppLink legacy = readyLink(0L);
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
			.require(AdxAccessScope.ScopeType.ADVERTISER, "advertiser-1", AdxAccessScope.PermissionLevel.VIEW);

		assertThatThrownBy(() -> service.detail("app-link-1")).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不存在或无权访问");
	}

	@Test
	void blocksArchiveWhileAReferenceStillExists() {
		when(appLinkMapper.selectOne(any())).thenReturn(readyLink(2L));
		when(monitorMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.archive("app-link-1", new AppLinkRequests.VersionedAction(2L, "obsolete")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("ADX_OBJECT_IN_USE");
	}

	private AdxAppLink readyLink(long version) {
		AdxUrlPolicy.Result checked = new AdxUrlPolicy().validate("https://8.8.4.4/click");
		AdxAppLink link = new AdxAppLink();
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

	private AdxProduct product(String advertiserId) {
		AdxProduct product = new AdxProduct();
		product.setId("product-1");
		product.setAdvertiserId(advertiserId);
		product.setCode("PRODUCT_1");
		product.setName("测试产品");
		product.setStatus("ACTIVE");
		product.setConfigVersion(1L);
		return product;
	}

	private AdxAdvertiser advertiser(String id) {
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId(id);
		advertiser.setAdvertiserCode("ADV_1");
		advertiser.setAdvertiserName("测试广告主");
		advertiser.setStatus("ACTIVE");
		advertiser.setCurrentDocumentId("document-1");
		return advertiser;
	}

	private AdxIntegrationDocument document(String advertiserId) {
		AdxIntegrationDocument document = new AdxIntegrationDocument();
		document.setId("document-1");
		document.setOwnerType("ADVERTISER");
		document.setOwnerId(advertiserId);
		document.setDocumentVersion(1L);
		document.setStatus("ACTIVE");
		document.setScanStatus("CLEAN");
		return document;
	}

}
