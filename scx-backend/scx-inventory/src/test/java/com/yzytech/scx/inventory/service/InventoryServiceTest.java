package com.yzytech.scx.inventory.service;

import com.yzytech.scx.inventory.entity.MediaAccount;
import com.yzytech.scx.inventory.entity.Placement;
import com.yzytech.scx.inventory.mapper.MediaAccountMapper;
import com.yzytech.scx.inventory.mapper.MediaAdapterConfigMapper;
import com.yzytech.scx.inventory.mapper.MediaCapabilityMapper;
import com.yzytech.scx.inventory.mapper.PlacementMapper;
import com.yzytech.scx.inventory.mapper.PlacementQualitySnapshotMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

	private final MediaAccountMapper accountMapper = mock(MediaAccountMapper.class);

	private final PlacementMapper placementMapper = mock(PlacementMapper.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final InventoryService service = new InventoryService(accountMapper, placementMapper,
			mock(MediaCapabilityMapper.class), mock(PlacementQualitySnapshotMapper.class),
			mock(MediaAdapterConfigMapper.class), scopeService);

	@Test
	void mediaAccountWithoutObjectScopeIsHidden() {
		MediaAccount account = account("account-1", "media-1");
		when(accountMapper.selectById("account-1")).thenReturn(account);
		when(scopeService.canRead("media-1")).thenReturn(false);

		assertThatThrownBy(() -> service.requireMediaAccount("account-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("资源不存在");
	}

	@Test
	void placementCapabilityCannotBypassMediaScope() {
		Placement placement = new Placement();
		placement.setId("placement-1");
		placement.setMediaAccountId("account-1");
		when(placementMapper.selectById("placement-1")).thenReturn(placement);
		when(accountMapper.selectById("account-1")).thenReturn(account("account-1", "media-1"));
		when(scopeService.canRead("media-1")).thenReturn(false);

		assertThatThrownBy(() -> service.getCapability("placement-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("资源不存在");
	}

	private MediaAccount account(String id, String mediaId) {
		MediaAccount account = new MediaAccount();
		account.setId(id);
		account.setMediaId(mediaId);
		return account;
	}

}
