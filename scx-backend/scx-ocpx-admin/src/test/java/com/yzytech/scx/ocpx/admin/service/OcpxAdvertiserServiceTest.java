package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.controller.OcpxAdvertiserController;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.admin.dto.ManagementRows;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.system.api.service.AdvertiserAccountProvisioner;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class OcpxAdvertiserServiceTest {

	private OcpxAdvertiserMapper advertiserMapper;

	private OcpxConfigRefreshPublisher configRefreshPublisher;

	private StringRedisTemplate redis;

	private AdvertiserAccountProvisioner accountProvisioner;

	private AdvertiserScope advertiserScope;

	private OcpxAdvertiserAccountService advertiserAccountService;

	private OcpxAdvertiserService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		advertiserMapper = mock(OcpxAdvertiserMapper.class);
		configRefreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		redis = mock(StringRedisTemplate.class);
		accountProvisioner = mock(AdvertiserAccountProvisioner.class);
		advertiserScope = mock(AdvertiserScope.class);
		advertiserAccountService = mock(OcpxAdvertiserAccountService.class);
		service = new OcpxAdvertiserService(advertiserMapper, configRefreshPublisher, redis, accountProvisioner,
				advertiserScope, advertiserAccountService);
	}

	@Test
	void createPersistsSubmittedFieldsWithoutUrlPolicy() {
		when(advertiserMapper.selectCount(any())).thenReturn(0L);
		AtomicReference<OcpxAdvertiser> stored = new AtomicReference<>();
		doAnswer(invocation -> {
			OcpxAdvertiser advertiser = invocation.getArgument(0);
			advertiser.setId("adv-1");
			stored.set(advertiser);
			return 1;
		}).when(advertiserMapper).insert(any(OcpxAdvertiser.class));
		when(advertiserMapper.selectById("adv-1")).thenAnswer(invocation -> stored.get());
		when(accountProvisioner.provision("adv-1", "广告主一", "adv-user", "Pass@123"))
			.thenReturn(new AdvertiserAccountProvisioner.ProvisionedAccount("user-1", "adv-user"));
		when(advertiserMapper.updateById(any(OcpxAdvertiser.class))).thenReturn(1);
		when(advertiserMapper.selectUsernamesByAdvertiserIds(any())).thenReturn(List.of(username("adv-1", "adv-user")));
		when(advertiserAccountService.balance("adv-1")).thenReturn(BigDecimal.ZERO);

		AdvertiserViews.Item result = service.create(new AdvertiserRequests.Create(" 广告主一 ", " advertiser-signing ",
				"http://127.0.0.1:8080/callback?oaid=__OAID__", " oaid=__OAID__ ", "adv-user", "Pass@123"));

		assertThat(result.getAdvertiserName()).isEqualTo("广告主一");
		assertThat(result.getCredentialConfigKey()).isEqualTo("advertiser-signing");
		assertThat(result.getBidUrl()).isEqualTo("http://127.0.0.1:8080/callback?oaid=__OAID__");
		assertThat(result.getMacroParams()).isEqualTo("oaid=__OAID__");
		assertThat(result.getUsername()).isEqualTo("adv-user");
		verify(accountProvisioner).provision("adv-1", "广告主一", "adv-user", "Pass@123");
		verify(advertiserAccountService).create("adv-1");
		verify(advertiserMapper).updateById(org.mockito.ArgumentMatchers
			.<OcpxAdvertiser>argThat(item -> "adv-1".equals(item.getId()) && "user-1".equals(item.getAccountUserId())));
		verify(configRefreshPublisher).mark();
	}

	@Test
	void provisioningFailureDoesNotPublishRefreshOrBindAccount() {
		when(advertiserMapper.selectCount(any())).thenReturn(0L);
		AtomicReference<OcpxAdvertiser> inserted = new AtomicReference<>();
		doAnswer(invocation -> {
			OcpxAdvertiser advertiser = invocation.getArgument(0);
			advertiser.setId("adv-1");
			inserted.set(advertiser);
			return 1;
		}).when(advertiserMapper).insert(any(OcpxAdvertiser.class));
		when(accountProvisioner.provision(anyString(), anyString(), anyString(), anyString()))
			.thenThrow(new IllegalArgumentException("用户名已存在"));

		assertThatThrownBy(() -> service.create(
				new AdvertiserRequests.Create("广告主一", null, "custom://private/callback", null, "adv-user", "Pass@123")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("用户名已存在");
		assertThat(inserted.get().getMacroParams()).isNull();
		verify(advertiserMapper, never()).updateById(any(OcpxAdvertiser.class));
		verify(configRefreshPublisher, never()).mark();
	}

	@Test
	void accountBindingUpdateFailureAbortsBeforeRefresh() {
		when(advertiserMapper.selectCount(any())).thenReturn(0L);
		doAnswer(invocation -> {
			OcpxAdvertiser advertiser = invocation.getArgument(0);
			advertiser.setId("adv-1");
			return 1;
		}).when(advertiserMapper).insert(any(OcpxAdvertiser.class));
		when(accountProvisioner.provision(anyString(), anyString(), anyString(), anyString()))
			.thenReturn(new AdvertiserAccountProvisioner.ProvisionedAccount("user-1", "adv-user"));
		when(advertiserMapper.updateById(any(OcpxAdvertiser.class))).thenReturn(0);

		assertThatThrownBy(() -> service.create(
				new AdvertiserRequests.Create("广告主一", null, "custom://private/callback", null, "adv-user", "Pass@123")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("绑定失败");
		verify(configRefreshPublisher, never()).mark();
	}

	@Test
	void rechargeDelegatesToTheIdempotentMysqlAccountService() {
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser("adv-1"));
		AdvertiserRequests.Recharge request = new AdvertiserRequests.Recharge("request-001", new BigDecimal("100"),
				"首充");
		AdvertiserViews.RechargeResult expected = new AdvertiserViews.RechargeResult();
		when(advertiserAccountService.recharge("adv-1", request)).thenReturn(expected);

		assertThat(service.recharge("adv-1", request)).isSameAs(expected);

		verify(advertiserAccountService).recharge("adv-1", request);
	}

	@Test
	void rechargeRejectsMissingAdvertiser() {
		when(advertiserMapper.selectById(anyString())).thenReturn(null);

		assertThatThrownBy(() -> service.recharge("adv-x",
				new AdvertiserRequests.Recharge("request-001", new BigDecimal("1"), null)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("广告主不存在");
		verifyNoInteractions(advertiserAccountService);
	}

	@Test
	void deductionDelegatesToTheIdempotentMysqlAccountService() {
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser("adv-1"));
		AdvertiserRequests.Deduction request = new AdvertiserRequests.Deduction("deduct-001", new BigDecimal("10"),
				"人工调账");
		AdvertiserViews.DeductionResult expected = new AdvertiserViews.DeductionResult();
		when(advertiserAccountService.deduct("adv-1", request)).thenReturn(expected);

		assertThat(service.deduct("adv-1", request)).isSameAs(expected);

		verify(advertiserAccountService).deduct("adv-1", request);
	}

	@Test
	void deductionRejectsMissingAdvertiser() {
		when(advertiserMapper.selectById(anyString())).thenReturn(null);

		assertThatThrownBy(() -> service.deduct("adv-x",
				new AdvertiserRequests.Deduction("deduct-001", new BigDecimal("1"), "人工调账")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("广告主不存在");
		verifyNoInteractions(advertiserAccountService);
	}

	@Test
	void rechargeRequiresRequestIdAndAPositiveAmount() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

		assertThat(validator.validate(new AdvertiserRequests.Recharge(null, null, null)))
			.extracting(violation -> violation.getPropertyPath().toString())
			.contains("requestId", "amount");
		assertThat(validator.validate(new AdvertiserRequests.Recharge("request-001", new BigDecimal("-0.01"), null)))
			.anyMatch(violation -> "amount".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(new AdvertiserRequests.Recharge("request-001", BigDecimal.ZERO, null)))
			.anyMatch(violation -> "amount".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(new AdvertiserRequests.Recharge("request-001", new BigDecimal("9.99"), null)))
			.isEmpty();
		assertThat(validator
			.validate(new AdvertiserRequests.Recharge("request-001", new BigDecimal("1.0000001"), null)))
			.anyMatch(violation -> "amount".equals(violation.getPropertyPath().toString()));
	}

	@Test
	void deductionRequiresRequestIdPositiveAmountAndReason() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

		assertThat(validator.validate(new AdvertiserRequests.Deduction(null, null, null)))
			.extracting(violation -> violation.getPropertyPath().toString())
			.contains("requestId", "amount", "remark");
		assertThat(validator.validate(new AdvertiserRequests.Deduction("deduct-001", BigDecimal.ZERO, "人工调账")))
			.anyMatch(violation -> "amount".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(new AdvertiserRequests.Deduction("deduct-001", new BigDecimal("1"), "  ")))
			.anyMatch(violation -> "remark".equals(violation.getPropertyPath().toString()));
		assertThat(
				validator.validate(new AdvertiserRequests.Deduction("deduct-001", new BigDecimal("1.000001"), "人工调账")))
			.isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void listReadsBalancesFromTheMysqlAccountService() {
		Page<OcpxAdvertiser> stored = new Page<>(1, 10);
		stored.setRecords(List.of(advertiser("adv-1"), advertiser("adv-2")));
		when(advertiserMapper.selectPage(any(), any())).thenReturn(stored);
		when(advertiserMapper.selectUsernamesByAdvertiserIds(any()))
			.thenReturn(List.of(username("adv-1", null), username("adv-2", null)));
		when(advertiserAccountService.balancesByIds(any()))
			.thenReturn(java.util.Map.of("adv-1", new BigDecimal("123.45"), "adv-2", BigDecimal.ZERO));

		List<AdvertiserViews.Item> records = (List<AdvertiserViews.Item>) service
			.list(new Page<>(1, 10), null, null)
			.getRecords();

		assertThat(records.get(0).getBalance()).isEqualByComparingTo("123.45");
		assertThat(records.get(1).getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(records).extracting(AdvertiserViews.Item::getUsername).containsOnlyNulls();
	}

	@Test
	@SuppressWarnings("unchecked")
	void allScopeAdvertiserNeverReceivesAnotherAdvertiserBalance() {
		Page<OcpxAdvertiser> stored = new Page<>(1, 10);
		stored.setRecords(List.of(advertiser("adv-own"), advertiser("adv-other")));
		when(advertiserMapper.selectPage(any(), any())).thenReturn(stored);
		when(advertiserMapper.selectUsernamesByAdvertiserIds(any())).thenReturn(List.of());
		when(advertiserScope.accountAdvertiserId(null)).thenReturn("adv-own");
		when(advertiserAccountService.balancesByIds(List.of("adv-own")))
			.thenReturn(java.util.Map.of("adv-own", new BigDecimal("88.000000")));

		List<AdvertiserViews.Item> records = (List<AdvertiserViews.Item>) service.list(new Page<>(1, 10), null, null)
			.getRecords();

		assertThat(records.get(0).getBalance()).isEqualByComparingTo("88.000000");
		assertThat(records.get(1).getBalance()).isNull();
		verify(advertiserAccountService).balancesByIds(List.of("adv-own"));
	}

	@Test
	void allScopeAdvertiserDetailHidesAnotherAdvertiserBalance() {
		when(advertiserMapper.selectById("adv-other")).thenReturn(advertiser("adv-other"));
		when(advertiserMapper.selectUsernamesByAdvertiserIds(any())).thenReturn(List.of());
		when(advertiserScope.accountAdvertiserId("adv-other")).thenReturn("adv-own");

		assertThat(service.detail("adv-other").getBalance()).isNull();
		verify(advertiserAccountService, never()).balance("adv-other");
	}

	@Test
	void optionsReturnIdAndNameForQuerySelect() {
		when(advertiserMapper.selectList(any())).thenReturn(List.of(advertiser("adv-1"), advertiser("adv-2")));

		assertThat(service.options()).extracting(AdvertiserViews.Option::getId, AdvertiserViews.Option::getName)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("adv-1", "广告主一"),
					org.assertj.core.groups.Tuple.tuple("adv-2", "广告主一"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void deleteAdvertiserCascadesInForeignKeySafeOrderAndClearsRuntimeState() {
		OcpxAdvertiser advertiser = advertiser("adv-1");
		advertiser.setAccountUserId("user-1");
		ManagementRows.RouteKey route = route("link-1", "media-1");
		when(advertiserMapper.selectByIdForUpdate("adv-1")).thenReturn(advertiser);
		when(advertiserMapper.selectCascadeRoutes("adv-1")).thenReturn(List.of(route));
		when(advertiserMapper.deleteById("adv-1")).thenReturn(1);

		service.delete("adv-1");

		verify(advertiserScope).requireAdministrator();
		verify(advertiserAccountService).assertDeletable("adv-1");
		verify(advertiserAccountService).deleteEmptyAccount("adv-1");
		InOrder deletion = inOrder(advertiserMapper);
		deletion.verify(advertiserMapper).selectByIdForUpdate("adv-1");
		deletion.verify(advertiserMapper).selectCascadeRoutes("adv-1");
		deletion.verify(advertiserMapper).deleteReportMatTemp("adv-1");
		deletion.verify(advertiserMapper).deleteReportMat("adv-1");
		deletion.verify(advertiserMapper).deleteCallbackLogs("adv-1");
		deletion.verify(advertiserMapper).deleteFeedbackDayLogs("adv-1");
		deletion.verify(advertiserMapper).deleteFeedbackLogs("adv-1");
		deletion.verify(advertiserMapper).deleteFlowLogs("adv-1");
		deletion.verify(advertiserMapper).deleteCreativeBindings("adv-1");
		deletion.verify(advertiserMapper).deleteCreatives("adv-1");
		deletion.verify(advertiserMapper).deleteDeductionRules("adv-1");
		deletion.verify(advertiserMapper).deleteMonitorTasks("adv-1");
		deletion.verify(advertiserMapper).deletePromotionLinks("adv-1");
		deletion.verify(advertiserMapper).deleteById("adv-1");
		verify(accountProvisioner).deprovision("adv-1", "user-1");
		ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
		verify(redis).delete(keys.capture());
		assertThat(keys.getValue()).containsExactlyInAnyOrder("ocpx:route:ratio:link-1:media-1",
				"ocpx:route:original-count:link-1:media-1",
				"ocpx:route:delivered-count:link-1:media-1");
		verify(configRefreshPublisher).mark();
	}

	@Test
	void deleteFailureDoesNotDeprovisionAccountOrPublishRuntimeRefresh() {
		OcpxAdvertiser advertiser = advertiser("adv-1");
		advertiser.setAccountUserId("user-1");
		when(advertiserMapper.selectByIdForUpdate("adv-1")).thenReturn(advertiser);
		when(advertiserMapper.selectCascadeRoutes("adv-1")).thenReturn(List.of());
		when(advertiserMapper.deleteById("adv-1")).thenReturn(0);

		assertThatThrownBy(() -> service.delete("adv-1")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("删除失败");
		verify(accountProvisioner, never()).deprovision(anyString(), anyString());
		verify(redis, never()).delete(org.mockito.ArgumentMatchers.<Collection<String>>any());
		verify(configRefreshPublisher, never()).mark();
	}

	@Test
	void rechargeEndpointCarriesPermissionAnnotation() throws NoSuchMethodException {
		var method = OcpxAdvertiserController.class.getMethod("recharge", String.class,
				AdvertiserRequests.Recharge.class);

		SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
		assertThat(permission).isNotNull();
		assertThat(Arrays.asList(permission.value())).contains("ocpx:advertiser:recharge");
		assertThat(method.getAnnotation(PostMapping.class).value()).contains("/{id}/recharge");
	}

	@Test
	void deductionEndpointCarriesItsOwnNonDelegablePermission() throws NoSuchMethodException {
		var method = OcpxAdvertiserController.class.getMethod("deduct", String.class,
				AdvertiserRequests.Deduction.class);

		SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
		assertThat(permission).isNotNull();
		assertThat(Arrays.asList(permission.value())).contains("ocpx:advertiser:deduct");
		assertThat(method.getAnnotation(PostMapping.class).value()).contains("/{id}/deduct");
	}

	private OcpxAdvertiser advertiser(String id) {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId(id);
		advertiser.setAdvertiserName("广告主一");
		advertiser.setBidUrl("https://example.test/callback");
		return advertiser;
	}

	private ManagementRows.AdvertiserUsername username(String advertiserId, String username) {
		ManagementRows.AdvertiserUsername row = new ManagementRows.AdvertiserUsername();
		row.setAdvertiserId(advertiserId);
		row.setUsername(username);
		return row;
	}

	private ManagementRows.RouteKey route(String promotionLinkId, String mediaId) {
		ManagementRows.RouteKey route = new ManagementRows.RouteKey();
		route.setPromotionLinkId(promotionLinkId);
		route.setMediaId(mediaId);
		return route;
	}

}
