package com.yzytech.scx.common.myabtis.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import java.time.LocalDateTime;
import lombok.Data;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MyMetaObjectHandlerTest {

	@Test
	void preservesExplicitSystemAuditOwner() {
		AuditRow row = new AuditRow();
		row.setCreateBy("system");
		BootUser user = new BootUser();
		user.setUsername("operator");

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user);
			new MyMetaObjectHandler().insertFill(SystemMetaObject.forObject(row));
		}

		assertThat(row.getCreateBy()).isEqualTo("system");
		assertThat(row.getCreateTime()).isNotNull();
	}

	@Data
	private static class AuditRow {

		private String createBy;

		private LocalDateTime createTime;

		private LocalDateTime updateTime;

		private String updateBy;

		private String delFlag;

	}

}
