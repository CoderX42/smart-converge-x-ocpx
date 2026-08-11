package com.yzytech.scx.ocpx.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.dto.EventRequests;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannelEventMapping;
import com.yzytech.scx.ocpx.admin.entity.OcpxEventDefinition;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelEventMappingMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventDefinitionMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class OcpxEventModelServiceTest {

	@Mock
	private OcpxEventDefinitionMapper definitionMapper;

	@Mock
	private OcpxChannelEventMappingMapper mappingMapper;

	@Mock
	private OcpxChannelMapper channelMapper;

	@Mock
	private OcpxEventChangeAuditMapper auditMapper;

	@Mock
	private OcpxAccessScope accessScope;

	@Mock
	private JdbcTemplate jdbcTemplate;

	private OcpxEventModelService service;

	@BeforeEach
	void setUp() {
		service = new OcpxEventModelService(definitionMapper, mappingMapper, channelMapper, auditMapper, accessScope,
				jdbcTemplate, new ObjectMapper());
	}

	@Test
	void channelScopedListReturnsAnEmptyPageWithoutQueryingOutOfScopeRows() {
		OcpxAccessScope.Snapshot snapshot = org.mockito.Mockito.mock(OcpxAccessScope.Snapshot.class);
		when(accessScope.current()).thenReturn(snapshot);
		when(snapshot.restriction(OcpxAccessScope.ScopeType.CHANNEL, OcpxAccessScope.PermissionLevel.VIEW))
			.thenReturn(new OcpxAccessScope.Restriction(false, List.of()));

		var result = service.mappings(1, 20, null, null, null, null);

		assertThat(result.getTotal()).isZero();
		assertThat(result.getRecords()).isEmpty();
		verify(mappingMapper, never()).selectPage(any(), any());
	}

	@Test
	void staleDefinitionUpdateReturnsExplicitConflict() {
		OcpxEventDefinition row = new OcpxEventDefinition();
		row.setId("event-1");
		row.setMappingState("CANONICAL");
		row.setConfigVersion(2L);
		when(definitionMapper.selectOne(any())).thenReturn(row);
		EventRequests.UpdateDefinition request = new EventRequests.UpdateDefinition("注册", "DOWNSTREAM_DELIVERY",
				"REGISTER", false, "DEVICE", 1, null, 1L);

		assertThatThrownBy(() -> service.updateDefinition("event-1", request))
			.isInstanceOfSatisfying(BootBusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
		verify(definitionMapper, never()).updateById(any(OcpxEventDefinition.class));
	}

	@Test
	void rejectsBodyTamperingThatMovesMappingToAnotherAdapter() {
		OcpxChannel channel = new OcpxChannel();
		channel.setId("channel-1");
		channel.setAdapterCode("STANDARD");
		channel.setStatus("ACTIVE");
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		EventRequests.CreateMapping request = new EventRequests.CreateMapping("channel-1", "OTHER", "REGISTER",
				"DOWNSTREAM_DELIVERY", "definition-1", "STANDARD_EVENT_V1", "HTTP_2XX", "ACTIVE", null);

		assertThatThrownBy(() -> service.createMapping("1234567890abcdef", request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Adapter");
		verify(mappingMapper, never()).insert(any(OcpxChannelEventMapping.class));
	}

}
