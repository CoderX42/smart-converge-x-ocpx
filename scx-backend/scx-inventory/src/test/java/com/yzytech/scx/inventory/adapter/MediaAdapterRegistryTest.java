package com.yzytech.scx.inventory.adapter;

import com.yzytech.scx.inventory.entity.MediaAccount;
import com.yzytech.scx.inventory.mapper.MediaAccountMapper;
import com.yzytech.scx.inventory.mapper.MediaAdapterConfigMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaAdapterRegistryTest {

	@Test
	void discoveredSandboxAdapterIsRegistered() {
		MediaAccountMapper accountMapper = mock(MediaAccountMapper.class);
		GenericSandboxMediaAdapter sandbox = new GenericSandboxMediaAdapter();
		MediaAdapterRegistry registry = new MediaAdapterRegistry(List.of(sandbox), accountMapper,
				mock(MediaAdapterConfigMapper.class));
		MediaAccount account = new MediaAccount();
		account.setId("account-1");
		account.setAdapterType("GENERIC_SANDBOX");
		when(accountMapper.selectById("account-1")).thenReturn(account);

		assertThat(registry.resolve("account-1")).isSameAs(sandbox);
	}

}
