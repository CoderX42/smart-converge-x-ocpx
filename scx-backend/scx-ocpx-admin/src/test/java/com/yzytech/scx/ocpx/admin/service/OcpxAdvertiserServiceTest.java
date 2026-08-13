package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.controller.OcpxAdvertiserController;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserRequests;
import com.yzytech.scx.ocpx.admin.dto.AdvertiserViews;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OcpxAdvertiserServiceTest {

	private OcpxAdvertiserMapper advertiserMapper;

	private OcpxUrlPolicy urlPolicy;

	private OcpxConfigRefreshPublisher configRefreshPublisher;

	private StringRedisTemplate redis;

	private ValueOperations<String, String> values;

	private OcpxAdvertiserService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		advertiserMapper = mock(OcpxAdvertiserMapper.class);
		urlPolicy = mock(OcpxUrlPolicy.class);
		configRefreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		service = new OcpxAdvertiserService(advertiserMapper, urlPolicy, configRefreshPublisher, redis);
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
		when(values.get("ocpx:advertiser:balance:adv-1")).thenReturn("123.45");
		when(values.get("ocpx:advertiser:balance:adv-2")).thenReturn(null);

		List<AdvertiserViews.Item> records = (List<AdvertiserViews.Item>) service
			.list(new Page<>(1, 10), null)
			.getRecords();

		assertThat(records.get(0).balance()).isEqualByComparingTo("123.45");
		assertThat(records.get(1).balance()).isEqualByComparingTo(BigDecimal.ZERO);
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

}
