package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.EventRequests;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxEventServiceScopeTest {

	@Test
	void editingCannotMoveAnExistingDefinitionOutOfAnUnauthorizedChannel() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		OcpxAccessScope accessScope = mock(OcpxAccessScope.class);
		OcpxEventService service = new OcpxEventService(jdbcTemplate, accessScope);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			RowMapper<Object> mapper = invocation.getArgument(1);
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.getString(1)).thenReturn("definition-1");
			when(resultSet.getString(2)).thenReturn("UPSTREAM");
			when(resultSet.getString(3)).thenReturn("channel-old");
			when(resultSet.getString(4)).thenReturn("REGISTER");
			when(resultSet.getString(5)).thenReturn("ACTIVE");
			return List.of(mapper.mapRow(resultSet, 0));
		});
		doThrow(new BootBusinessException(HttpStatus.FORBIDDEN.value(), "对象不存在或无权访问"))
			.when(accessScope)
			.require(OcpxAccessScope.ScopeType.CHANNEL, "channel-old", OcpxAccessScope.PermissionLevel.MANAGE);

		EventRequests.SaveDefinition request = new EventRequests.SaveDefinition("UPSTREAM", "channel-new",
				"REGISTER", "注册", "ACTIVE", 10, null);

		assertThatThrownBy(() -> service.saveDefinition("definition-1", request))
			.isInstanceOf(BootBusinessException.class);
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

}
