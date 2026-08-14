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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class OcpxAdvertiserServiceTest {

	private OcpxAdvertiserMapper advertiserMapper;

	private OcpxConfigRefreshPublisher configRefreshPublisher;

	private StringRedisTemplate redis;

	private ValueOperations<String, String> values;

	private AdvertiserAccountProvisioner accountProvisioner;

	private AdvertiserScope advertiserScope;

	private OcpxAdvertiserService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		advertiserMapper = mock(OcpxAdvertiserMapper.class);
		configRefreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		accountProvisioner = mock(AdvertiserAccountProvisioner.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxAdvertiserService(advertiserMapper, configRefreshPublisher, redis, accountProvisioner,
				advertiserScope);
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

		AdvertiserViews.Item result = service.create(new AdvertiserRequests.Create(" 广告主一 ", " mi-signing ",
				"http://127.0.0.1:8080/callback?oaid=__OAID__", " oaid=__OAID__ ", "adv-user", "Pass@123"));

		assertThat(result.advertiserName()).isEqualTo("广告主一");
		assertThat(result.credentialConfigKey()).isEqualTo("mi-signing");
		assertThat(result.bidUrl()).isEqualTo("http://127.0.0.1:8080/callback?oaid=__OAID__");
		assertThat(result.macroParams()).isEqualTo("oaid=__OAID__");
		assertThat(result.username()).isEqualTo("adv-user");
		verify(accountProvisioner).provision("adv-1", "广告主一", "adv-user", "Pass@123");
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
			.isInstanceOf(IllegalArgumentException.class)
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
	void rechargeAccumulatesBalanceInRedis() {
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser("adv-1"));

		service.recharge("adv-1", new AdvertiserRequests.Recharge(new BigDecimal("100")));
		service.recharge("adv-1", new AdvertiserRequests.Recharge(new BigDecimal("50.5")));

		verify(values).increment("ocpx:advertiser:balance:adv-1", 100.0);
		verify(values).increment("ocpx:advertiser:balance:adv-1", 50.5);
	}

	@Test
	void rechargeRejectsMissingAdvertiser() {
		when(advertiserMapper.selectById(anyString())).thenReturn(null);

		assertThatThrownBy(() -> service.recharge("adv-x", new AdvertiserRequests.Recharge(new BigDecimal("1"))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("广告主不存在");
		verifyNoInteractions(values);
	}

	@Test
	void rechargeBalanceMustBePresentAndNonNegative() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

		assertThat(validator.validate(new AdvertiserRequests.Recharge(null)))
			.anyMatch(violation -> "balance".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(new AdvertiserRequests.Recharge(new BigDecimal("-0.01"))))
			.anyMatch(violation -> "balance".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(new AdvertiserRequests.Recharge(BigDecimal.ZERO)))
			.noneMatch(violation -> "balance".equals(violation.getPropertyPath().toString()));
		assertThat(validator.validate(new AdvertiserRequests.Recharge(new BigDecimal("9.99"))))
			.noneMatch(violation -> "balance".equals(violation.getPropertyPath().toString()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void listBackfillsBalanceFromRedisWithZeroFallback() {
		Page<OcpxAdvertiser> stored = new Page<>(1, 10);
		stored.setRecords(List.of(advertiser("adv-1"), advertiser("adv-2")));
		when(advertiserMapper.selectPage(any(), any())).thenReturn(stored);
		when(advertiserMapper.selectUsernamesByAdvertiserIds(any()))
			.thenReturn(List.of(username("adv-1", null), username("adv-2", null)));
		when(values.multiGet(any())).thenReturn(Arrays.asList("123.45", null));

		List<AdvertiserViews.Item> records = (List<AdvertiserViews.Item>) service.list(new Page<>(1, 10), null, null)
			.getRecords();

		assertThat(records.get(0).balance()).isEqualByComparingTo("123.45");
		assertThat(records.get(1).balance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(records).extracting(AdvertiserViews.Item::username).containsOnlyNulls();
	}

	@Test
	void optionsReturnIdAndNameForQuerySelect() {
		when(advertiserMapper.selectList(any())).thenReturn(List.of(advertiser("adv-1"), advertiser("adv-2")));

		assertThat(service.options()).extracting(AdvertiserViews.Option::id, AdvertiserViews.Option::name)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("adv-1", "广告主一"),
					org.assertj.core.groups.Tuple.tuple("adv-2", "广告主一"));
	}

	@Test
	void deleteUnusedAdvertiserDeprovisionsAccountAndClearsRuntimeState() {
		OcpxAdvertiser advertiser = advertiser("adv-1");
		advertiser.setAccountUserId("user-1");
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser);
		when(advertiserMapper.countReferences("adv-1")).thenReturn(0);
		when(advertiserMapper.deleteById("adv-1")).thenReturn(1);

		service.delete("adv-1");

		verify(advertiserScope).requireAdministrator();
		verify(accountProvisioner).deprovision("adv-1", "user-1");
		verify(redis).delete("ocpx:advertiser:balance:adv-1");
		verify(configRefreshPublisher).mark();
	}

	@Test
	void deleteRejectsAdvertiserWithBusinessOrHistoricalReferences() {
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser("adv-1"));
		when(advertiserMapper.countReferences("adv-1")).thenReturn(1);

		assertThatThrownBy(() -> service.delete("adv-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("历史数据");
		verify(advertiserMapper, never()).deleteById(anyString());
		verify(accountProvisioner, never()).deprovision(anyString(), anyString());
		verify(redis, never()).delete(anyString());
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

}
