package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.ProductRequests;
import com.yzytech.scx.ocpx.admin.dto.ProductViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.entity.OcpxProductChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxProductLogoObject;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductEventPolicyMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductLogoObjectMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
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

class OcpxProductServiceTest {

	private final OcpxProductMapper productMapper = mock(OcpxProductMapper.class);

	private final OcpxProductEventPolicyMapper policyMapper = mock(OcpxProductEventPolicyMapper.class);

	private final OcpxProductLogoObjectMapper logoMapper = mock(OcpxProductLogoObjectMapper.class);

	private final OcpxProductChangeAuditMapper auditMapper = mock(OcpxProductChangeAuditMapper.class);

	private final OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);

	private final OcpxAppLinkMapper appLinkMapper = mock(OcpxAppLinkMapper.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxConfigChangeService configChangeService = mock(OcpxConfigChangeService.class);

	private final StorageUploadService storageUploadService = mock(StorageUploadService.class);

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private OcpxProductService service;

	@BeforeEach
	void setUp() {
		service = new OcpxProductService(productMapper, policyMapper, logoMapper, auditMapper, advertiserMapper,
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
			.require(OcpxAccessScope.ScopeType.ADVERTISER, "advertiser-1",
					OcpxAccessScope.PermissionLevel.VIEW);

		assertThatThrownBy(() -> service.detail("product-1"))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不存在或无权访问");
	}

	@Test
	void createsIndependentProductWithIdempotencyAndNoStorageReferenceLeak() {
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(activeAdvertiser());
		when(productMapper.selectCount(any())).thenReturn(0L);
		when(productMapper.insert(any(OcpxProduct.class))).thenAnswer(invocation -> {
			((OcpxProduct) invocation.getArgument(0)).setId("product-1");
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

		ArgumentCaptor<OcpxProduct> product = ArgumentCaptor.forClass(OcpxProduct.class);
		verify(productMapper).insert(product.capture());
		assertThat(product.getValue().getNormalizedName()).isEqualTo("测试产品");
		assertThat(product.getValue().getCreateRequestHash()).hasSize(64);
		verify(auditMapper).insert(any(OcpxProductChangeAudit.class));
		assertThat(Arrays.stream(ProductViews.Detail.class.getRecordComponents()).map(component -> component.getName()))
			.doesNotContain("objectKey", "storageConfigId", "storageProvider", "sha256");
	}

	@Test
	void rejectsStaleProductUpdateBeforeOverwritingPolicies() {
		when(productMapper.selectOne(any())).thenReturn(product(5L));

		assertThatThrownBy(() -> service.update("product-1",
				new ProductRequests.Update("测试产品", null, null, "ACTIVE", 4L, List.of())))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("OCPX_VERSION_CONFLICT");
	}

	@Test
	void blocksArchiveWhileAnAppLinkStillReferencesTheProduct() {
		when(productMapper.selectOne(any())).thenReturn(product(2L));
		when(appLinkMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.archive("product-1", new ProductRequests.VersionedAction(2L, "obsolete")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("OCPX_OBJECT_IN_USE", "1 个应用链接");
	}

	@Test
	void deletesNewManagedLogoWhenMetadataPersistenceFails() {
		OcpxProduct product = product(1L);
		when(productMapper.selectById("product-1")).thenReturn(product);
		when(productMapper.selectOne(any())).thenReturn(product);
		ManagedStorageObject stored = new ManagedStorageObject("storage-1", "local", "managed/logo.png", null);
		when(storageUploadService.uploadManaged(any(), anyString(), anyString(), anyLong())).thenReturn(stored);
		doThrow(new IllegalStateException("database unavailable")).when(logoMapper)
			.insert(any(OcpxProductLogoObject.class));
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
		OcpxProduct product = product(1L);
		when(productMapper.selectById("product-1")).thenReturn(product);
		when(productMapper.selectOne(any())).thenReturn(product);
		ManagedStorageObject stored = new ManagedStorageObject("storage-1", "local", "managed/logo.png", null);
		when(storageUploadService.uploadManaged(any(), anyString(), anyString(), anyLong())).thenReturn(stored);
		doThrow(new IllegalStateException("database unavailable")).when(logoMapper)
			.insert(any(OcpxProductLogoObject.class));
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

	private OcpxAdvertiser activeAdvertiser() {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setAdvertiserCode("adv_test");
		advertiser.setAdvertiserName("测试广告主");
		advertiser.setStatus("ACTIVE");
		return advertiser;
	}

	private OcpxProduct product(long version) {
		OcpxProduct product = new OcpxProduct();
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
