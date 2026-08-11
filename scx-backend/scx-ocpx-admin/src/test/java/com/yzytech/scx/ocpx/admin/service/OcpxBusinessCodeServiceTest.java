package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventDefinitionMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OcpxBusinessCodeServiceTest {

	private final OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);

	private final OcpxChannelMapper channelMapper = mock(OcpxChannelMapper.class);

	private final OcpxProductMapper productMapper = mock(OcpxProductMapper.class);

	private final OcpxEventDefinitionMapper eventMapper = mock(OcpxEventDefinitionMapper.class);

	private final OcpxBusinessCodeService service = new OcpxBusinessCodeService(advertiserMapper, channelMapper,
			productMapper, eventMapper);

	@Test
	void generatesTheFourCanonicalFormatsAndChecksCreatePermissions() {
		when(advertiserMapper.selectCount(any())).thenReturn(0L);
		when(channelMapper.selectCount(any())).thenReturn(0L);
		when(productMapper.selectCount(any())).thenReturn(0L);
		when(eventMapper.selectCount(any())).thenReturn(0L);

		try (MockedStatic<StpUtil> security = mockStatic(StpUtil.class)) {
			List<String> codes = List.of(service.generate("ADVERTISER").code(), service.generate("CHANNEL").code(),
					service.generate("PRODUCT").code(), service.generate("EVENT").code());

			assertThat(codes.get(0)).matches("adv_[0-9a-f]{16}");
			assertThat(codes.get(1)).matches("chn_[0-9a-f]{16}");
			assertThat(codes.get(2)).matches("prd_[0-9a-f]{16}");
			assertThat(codes.get(3)).matches("EVT_[0-9A-F]{16}");
			security.verify(() -> StpUtil.checkPermission("ocpx:advertiser:add"));
			security.verify(() -> StpUtil.checkPermission("ocpx:channel:write"));
			security.verify(() -> StpUtil.checkPermission("ocpx:product:add"));
			security.verify(() -> StpUtil.checkPermission("ocpx:event:add"));
		}
	}

}
