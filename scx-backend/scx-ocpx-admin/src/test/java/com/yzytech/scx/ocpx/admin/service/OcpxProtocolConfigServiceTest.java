package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.controller.OcpxProtocolConfigController;
import com.yzytech.scx.ocpx.admin.dto.ProtocolConfigRequests;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProtocolAllowedHostMapper;
import com.yzytech.scx.ocpx.entity.OcpxProtocolAllowedHost;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OcpxProtocolConfigServiceTest {

	private OcpxProtocolAllowedHostMapper mapper;

	private OcpxConfigRefreshPublisher refreshPublisher;

	private AdvertiserScope advertiserScope;

	private OcpxProtocolConfigService service;

	@BeforeEach
	void setUp() {
		mapper = mock(OcpxProtocolAllowedHostMapper.class);
		refreshPublisher = mock(OcpxConfigRefreshPublisher.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxProtocolConfigService(mapper, refreshPublisher, advertiserScope);
	}

	@Test
	void mapperIsRegisteredForSpringDiscovery() {
		assertThat(OcpxProtocolAllowedHostMapper.class).hasAnnotation(Mapper.class);
	}

	@Test
	void replacesNormalizedHostsAndPublishesEngineRefresh() {
		when(mapper.insert(any(OcpxProtocolAllowedHost.class))).thenReturn(1);

		var result = service.updateAllowedHosts(OcpxUpstreamProtocol.AERIM,
				new ProtocolConfigRequests.UpdateAllowedHosts(List.of(" D.AERIM.CN. ", "d.aerim.cn", "c.aerim.cn")));

		verify(advertiserScope).requireAdministrator();
		verify(mapper).delete(any());
		ArgumentCaptor<OcpxProtocolAllowedHost> values = ArgumentCaptor.forClass(OcpxProtocolAllowedHost.class);
		verify(mapper, org.mockito.Mockito.times(2)).insert(values.capture());
		assertThat(values.getAllValues()).extracting(OcpxProtocolAllowedHost::getHost)
			.containsExactly("d.aerim.cn", "c.aerim.cn");
		assertThat(result.getAllowedHosts()).containsExactly("d.aerim.cn", "c.aerim.cn");
		verify(refreshPublisher).mark();
	}

	@Test
	void rejectsUrlAndNonHostConfigurableProtocolBeforeWriting() {
		assertThatThrownBy(() -> service.updateAllowedHosts(OcpxUpstreamProtocol.AERIM,
				new ProtocolConfigRequests.UpdateAllowedHosts(List.of("https://d.aerim.cn/path"))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("只允许填写精确域名");
		assertThatThrownBy(() -> service.updateAllowedHosts(OcpxUpstreamProtocol.TEMPLATE_GET,
				new ProtocolConfigRequests.UpdateAllowedHosts(List.of())))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不支持域名白名单");
		verifyNoInteractions(mapper, refreshPublisher);
	}

	@Test
	void controllerUsesDedicatedAdministratorPermission() throws NoSuchMethodException {
		var list = OcpxProtocolConfigController.class.getMethod("list");
		var update = OcpxProtocolConfigController.class.getMethod("updateAllowedHosts", OcpxUpstreamProtocol.class,
				ProtocolConfigRequests.UpdateAllowedHosts.class);

		assertThat(list.getAnnotation(SaCheckPermission.class).value())
			.containsExactly(OcpxProtocolConfigController.MANAGE_PERMISSION);
		assertThat(update.getAnnotation(SaCheckPermission.class).value())
			.containsExactly(OcpxProtocolConfigController.MANAGE_PERMISSION);
	}

}
