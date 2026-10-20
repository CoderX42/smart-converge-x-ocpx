package com.yzytech.scx.ocpx.admin.rta;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.mapper.RtaManagementSqlMapper;
import com.yzytech.scx.ocpx.admin.service.AdvertiserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RtaManagementTest {

	RtaManagementSqlMapper mapper;

	AdvertiserScope scope;

	RtaManagement service;

	@BeforeEach
	void setup() {
		mapper = mock(RtaManagementSqlMapper.class);
		scope = mock(AdvertiserScope.class);
		service = new RtaManagement(mapper, scope, mock(StringRedisTemplate.class));
	}

	Map<String, Object> bindingBody() {
		return new HashMap<>(Map.of("monitorTaskId", "t1", "upstreamId", "u1", "productId", "p1", "externalTaskId",
				"e1", "enabled", true, "version", "0"));
	}

	void route(String mediaProtocol, String upstreamProtocol) {
		when(mapper.lockTask("t1"))
			.thenReturn(List.of(Map.of("advertiser_id", "a1", "media_id", "m1", "promotion_link_id", "l1")));
		when(mapper.selectSaveRtaMediaAccess("m1"))
			.thenReturn(List.of(Map.of("media_id", "m1", "protocol", mediaProtocol)));
		when(mapper.findUpstream("u1")).thenReturn(Map.of("advertiser_id", "a1", "protocol", upstreamProtocol));
		when(mapper.countValidRoute(any(), anyString(), any(), anyBoolean())).thenReturn(1L);
		when(mapper.selectSaveRtaBinding(any(), anyString(), anyString(), anyString(), anyString())).thenReturn(0L);
	}

	@Test
	void oppoMediaCannotBindGenericUpstream() {
		route("OPPO", "GENERIC_ASYNC");
		assertThatThrownBy(() -> service.save("bindings", bindingBody())).isInstanceOf(BootBusinessException.class)
			.hasMessage("OPPO 媒体只能绑定微风 RTA 接入");
		verify(mapper, never()).insertSaveRtaBinding(any(), any(), any(), any(), anyBoolean());
	}

	@Test
	void genericMediaCanBindGenericUpstream() {
		route("GENERIC_ASYNC", "GENERIC_ASYNC");
		service.save("bindings", bindingBody());
		verify(mapper).insertSaveRtaBinding("t1", "u1", "p1", "e1", true);
		verify(mapper).updateSaveMonitorTask(true, "t1");
	}

	@Test
	void deleteBindingChecksOwnerVersionAndDisablesTask() {
		when(mapper.lockTask("t1")).thenReturn(List.of(Map.of("advertiser_id", "a1")));
		when(mapper.deleteRtaBinding("t1", 3)).thenReturn(1);
		service.deleteBinding("t1", 3);
		verify(scope).requireOwner("a1");
		verify(mapper).updateSaveMonitorTask(false, "t1");

		when(mapper.deleteRtaBinding("t1", 2)).thenReturn(0);
		assertThatThrownBy(() -> service.deleteBinding("t1", 2)).hasMessage("配置已变更，请刷新后重试");
	}

	@Test
	void deleteBindingRejectsForeignTask() {
		when(mapper.lockTask("t1")).thenReturn(List.of(Map.of("advertiser_id", "a2")));
		doThrow(new BootBusinessException("无权访问其他广告主数据")).when(scope).requireOwner("a2");
		assertThatThrownBy(() -> service.deleteBinding("t1", 1)).hasMessage("无权访问其他广告主数据");
		verify(mapper, never()).deleteRtaBinding(any(), anyLong());
	}

	@Test
	void upstreamInUseCannotBeDeleted() {
		when(mapper.findUpstream("u1")).thenReturn(Map.of("id", "u1"));
		when(mapper.countUpstreamBindings("u1")).thenReturn(2L);
		assertThatThrownBy(() -> service.deleteUpstream("u1", 1)).hasMessage("仍被 2 个任务使用，请先解绑");
		verify(mapper, never()).deleteRtaUpstream(any(), anyLong());

		when(mapper.countUpstreamBindings("u1")).thenReturn(0L);
		when(mapper.deleteRtaUpstream("u1", 1)).thenReturn(1);
		service.deleteUpstream("u1", 1);
		verify(mapper).deleteRtaUpstream("u1", 1);
	}

	@Test
	void mediaAccessInUseCannotBeDeleted() {
		when(mapper.selectSaveRtaMediaAccess("m1")).thenReturn(List.of(Map.of("media_id", "m1")));
		when(mapper.countMediaBindings("m1")).thenReturn(1L);
		assertThatThrownBy(() -> service.deleteMediaAccess("m1", 1)).hasMessage("仍被 1 个任务使用，请先解绑");

		when(mapper.countMediaBindings("m1")).thenReturn(0L);
		when(mapper.deleteRtaMediaAccess("m1", 1)).thenReturn(0);
		assertThatThrownBy(() -> service.deleteMediaAccess("m1", 1)).hasMessage("配置已变更，请刷新后重试");
	}

	@Test
	void configDeletesRequireSystemAdministrator() {
		doThrow(new BootBusinessException("仅系统管理员可以维护资金与账号")).when(scope).requireSystemAdministrator();
		assertThatThrownBy(() -> service.deleteUpstream("u1", 1)).isInstanceOf(BootBusinessException.class);
		assertThatThrownBy(() -> service.deleteMediaAccess("m1", 1)).isInstanceOf(BootBusinessException.class);
		verifyNoInteractions(mapper);
	}

}
