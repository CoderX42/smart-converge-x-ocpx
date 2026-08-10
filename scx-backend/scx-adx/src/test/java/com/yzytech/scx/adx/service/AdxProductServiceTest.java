package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.dto.ProductRequests;
import com.yzytech.scx.adx.dto.ProductViews;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxProduct;
import com.yzytech.scx.adx.entity.AdxProductChangeAudit;
import com.yzytech.scx.adx.entity.AdxProductLogoObject;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxAppLinkMapper;
import com.yzytech.scx.adx.mapper.AdxProductChangeAuditMapper;
import com.yzytech.scx.adx.mapper.AdxProductEventPolicyMapper;
import com.yzytech.scx.adx.mapper.AdxProductLogoObjectMapper;
import com.yzytech.scx.adx.mapper.AdxProductMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.service.StorageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdxProductServiceTest {

	private final AdxProductMapper productMapper = mock(AdxProductMapper.class);

	private final AdxProductEventPolicyMapper policyMapper = mock(AdxProductEventPolicyMapper.class);

	private final AdxProductLogoObjectMapper logoMapper = mock(AdxProductLogoObjectMapper.class);

	private final AdxProductChangeAuditMapper auditMapper = mock(AdxProductChangeAuditMapper.class);

	private final AdxAdvertiserMapper advertiserMapper = mock(AdxAdvertiserMapper.class);

	private final AdxAppLinkMapper appLinkMapper = mock(AdxAppLinkMapper.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxConfigChangeService configChangeService = mock(AdxConfigChangeService.class);

	private final StorageUploadService storageUploadService = mock(StorageUploadService.class);

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private AdxProductService service;

	@BeforeEach
	void setUp() {
		service = new AdxProductService(productMapper, policyMapper, logoMapper, auditMapper, advertiserMapper,
				appLinkMapper, accessScope, configChangeService, storageUploadService, jdbcTemplate);
		when(policyMapper.selectList(any())).thenReturn(List.of());
		when(appLinkMapper.selectCount(any())).thenReturn(0L);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString())).thenReturn(0L);
		when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyString()))
			.thenReturn(List.of());
	}

	@Test
	void hidesProductDetailWhenAdvertiserScopeDoesNotAllowIt() {
		when(productMapper.selectById("product-1")).thenReturn(product(1L));
		doThrow(new BootBusinessException(403, "对象不存在或无权访问"))
			.when(accessScope)
			.require(AdxAccessScope.ScopeType.ADVERTISER, "advertiser-1",
					AdxAccessScope.PermissionLevel.VIEW);

		assertThatThrownBy(() -> service.detail("product-1"))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不存在或无权访问");
	}

	@Test
	void createsIndependentProductWithIdempotencyAndNoStorageReferenceLeak() {
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(activeAdvertiser());
		when(productMapper.selectCount(any())).thenReturn(0L);
		when(productMapper.insert(any(AdxProduct.class))).thenAnswer(invocation -> {
			((AdxProduct) invocation.getArgument(0)).setId("product-1");
			return 1;
		});
		ProductRequests.Create request = new ProductRequests.Create("advertiser-1", "product_code", "测试产品",
				"com.example.app", "description", "ACTIVE", List.of());

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			ProductViews.Detail result = service.create("product-request-0001", request);

			assertThat(result.id()).isEqualTo("product-1");
			assertThat(result.configVersion()).isEqualTo(1);
			assertThat(result.eventPolicyRevision()).isEqualTo(1);
		}

		ArgumentCaptor<AdxProduct> product = ArgumentCaptor.forClass(AdxProduct.class);
		verify(productMapper).insert(product.capture());
		assertThat(product.getValue().getNormalizedName()).isEqualTo("测试产品");
		assertThat(product.getValue().getCreateRequestHash()).hasSize(64);
		verify(auditMapper).insert(any(AdxProductChangeAudit.class));
		assertThat(Arrays.stream(ProductViews.Detail.class.getRecordComponents()).map(component -> component.getName()))
			.doesNotContain("objectKey", "storageConfigId", "storageProvider", "sha256");
	}

	@Test
	void rejectsStaleProductUpdateBeforeOverwritingPolicies() {
		when(productMapper.selectOne(any())).thenReturn(product(5L));

		assertThatThrownBy(() -> service.update("product-1",
				new ProductRequests.Update("测试产品", null, null, "ACTIVE", 4L, List.of())))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("ADX_VERSION_CONFLICT");
	}

	@Test
	void blocksArchiveWhileAnAppLinkStillReferencesTheProduct() {
		when(productMapper.selectOne(any())).thenReturn(product(2L));
		when(appLinkMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.archive("product-1", new ProductRequests.VersionedAction(2L, "obsolete")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("ADX_OBJECT_IN_USE", "1 个应用链接");
	}

	@Test
	void deletesNewManagedLogoWhenMetadataPersistenceFails() {
		AdxProduct product = product(1L);
		when(productMapper.selectById("product-1")).thenReturn(product);
		when(productMapper.selectOne(any())).thenReturn(product);
		ManagedStorageObject stored = new ManagedStorageObject("storage-1", "local", "managed/logo.png", null);
		when(storageUploadService.uploadManaged(any(), anyString(), anyString(), anyLong())).thenReturn(stored);
		doThrow(new IllegalStateException("database unavailable")).when(logoMapper)
			.insert(any(AdxProductLogoObject.class));
		byte[] onePixelPng = Base64.getDecoder()
			.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
		MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", onePixelPng);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			assertThatThrownBy(() -> service.uploadLogo("product-1", 1L, file))
				.isInstanceOf(IllegalStateException.class);
		}

		verify(storageUploadService).delete(stored);
	}

	@Test
	void deletesNewManagedLogoWhenTransactionCompletesWithRollback() {
		AdxProduct product = product(1L);
		when(productMapper.selectById("product-1")).thenReturn(product);
		when(productMapper.selectOne(any())).thenReturn(product);
		ManagedStorageObject stored = new ManagedStorageObject("storage-1", "local", "managed/logo.png", null);
		when(storageUploadService.uploadManaged(any(), anyString(), anyString(), anyLong())).thenReturn(stored);
		doThrow(new IllegalStateException("database unavailable")).when(logoMapper)
			.insert(any(AdxProductLogoObject.class));
		byte[] onePixelPng = Base64.getDecoder()
			.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
		MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", onePixelPng);

		TransactionSynchronizationManager.initSynchronization();
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserName).thenReturn("operator");
			assertThatThrownBy(() -> service.uploadLogo("product-1", 1L, file))
				.isInstanceOf(IllegalStateException.class);
			TransactionSynchronizationManager.getSynchronizations()
				.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}

		verify(storageUploadService).delete(stored);
	}

	private AdxAdvertiser activeAdvertiser() {
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setAdvertiserCode("adv_test");
		advertiser.setAdvertiserName("测试广告主");
		advertiser.setStatus("ACTIVE");
		return advertiser;
	}

	private AdxProduct product(long version) {
		AdxProduct product = new AdxProduct();
		product.setId("product-1");
		product.setAdvertiserId("advertiser-1");
		product.setCode("product_code");
		product.setName("测试产品");
		product.setNormalizedName("测试产品");
		product.setStatus("ACTIVE");
		product.setConfigVersion(version);
		product.setEventPolicyRevision(1L);
		return product;
	}

}
