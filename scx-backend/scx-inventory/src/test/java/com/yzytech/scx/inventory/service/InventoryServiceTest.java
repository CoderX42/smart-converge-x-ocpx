package com.yzytech.scx.inventory.service;

import com.yzytech.scx.delivery.entity.MediaChannel;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.inventory.entity.MediaAccount;
import com.yzytech.scx.inventory.entity.Placement;
import com.yzytech.scx.inventory.mapper.MediaAccountMapper;
import com.yzytech.scx.inventory.mapper.MediaAdapterConfigMapper;
import com.yzytech.scx.inventory.mapper.MediaCapabilityMapper;
import com.yzytech.scx.inventory.mapper.PlacementMapper;
import com.yzytech.scx.inventory.mapper.PlacementQualitySnapshotMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

	private final MediaAccountMapper accountMapper = mock(MediaAccountMapper.class);

	private final PlacementMapper placementMapper = mock(PlacementMapper.class);

	private final MediaCapabilityMapper capabilityMapper = mock(MediaCapabilityMapper.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final MediaChannelMapper channelMapper = mock(MediaChannelMapper.class);

	private final InventoryService service = new InventoryService(accountMapper, placementMapper, capabilityMapper,
			mock(PlacementQualitySnapshotMapper.class), mock(MediaAdapterConfigMapper.class), channelMapper,
			scopeService);

	@Test
	void newMediaAccountMustBelongToActiveChannelInSameMedia() {
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(channelMapper.selectById("channel-1")).thenReturn(channel("channel-1", "media-1", "ACTIVE"));
		when(accountMapper.insert(any(MediaAccount.class))).thenReturn(1);

		MediaAccount account = service.createMediaAccount("media-1", "channel-1", " 主账户 ", null, "external-1");

		assertThat(account.getMediaId()).isEqualTo("media-1");
		assertThat(account.getChannelId()).isEqualTo("channel-1");
		assertThat(account.getAccountName()).isEqualTo("主账户");
		assertThat(account.getAdapterType()).isEqualTo("GENERIC_SANDBOX");
		verify(accountMapper).insert(account);
	}

	@Test
	void newMediaAccountRejectsMissingDisabledAndCrossMediaChannel() {
		when(scopeService.canWrite("media-1")).thenReturn(true);

		assertThatThrownBy(() -> service.createMediaAccount("media-1", null, "账户", null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("请选择投放渠道");

		when(channelMapper.selectById("disabled")).thenReturn(channel("disabled", "media-1", "DISABLED"));
		assertThatThrownBy(() -> service.createMediaAccount("media-1", "disabled", "账户", null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("请选择已启用的投放渠道");

		when(channelMapper.selectById("other-media")).thenReturn(channel("other-media", "media-2", "ACTIVE"));
		assertThatThrownBy(() -> service.createMediaAccount("media-1", "other-media", "账户", null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("资源不存在");
	}

	@Test
	void legacyMediaAccountCanStillUpdateMetadataWithoutAssigningChannel() {
		MediaAccount account = account("account-1", "media-1");
		when(accountMapper.selectById("account-1")).thenReturn(account);
		when(scopeService.canRead("media-1")).thenReturn(true);
		when(scopeService.canWrite("media-1")).thenReturn(true);

		MediaAccount updated = service.updateMediaAccount("account-1", null, " 新名称 ", "external-2", null);

		assertThat(updated.getChannelId()).isNull();
		assertThat(updated.getAccountName()).isEqualTo("新名称");
		assertThat(updated.getAccountIdExternal()).isEqualTo("external-2");
		verify(accountMapper).updateById(account);
	}

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

	@Test
	void rejectsDeletingReferencedMediaAccount() {
		MediaAccount account = account("account-1", "media-1");
		when(accountMapper.selectById("account-1")).thenReturn(account);
		when(scopeService.canRead("media-1")).thenReturn(true);
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(accountMapper.countReferences("account-1")).thenReturn(1L);

		assertThatThrownBy(() -> service.deleteMediaAccount("account-1")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("仍被广告位");
	}

	@Test
	void deletesUnreferencedPlacementAndItsCapabilityMatrix() {
		Placement placement = new Placement();
		placement.setId("placement-1");
		placement.setMediaAccountId("account-1");
		when(placementMapper.selectById("placement-1")).thenReturn(placement);
		when(accountMapper.selectById("account-1")).thenReturn(account("account-1", "media-1"));
		when(scopeService.canRead("media-1")).thenReturn(true);
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(placementMapper.countReferences("placement-1")).thenReturn(0L);
		when(placementMapper.deleteById("placement-1")).thenReturn(1);

		assertThat(service.deletePlacement("placement-1")).isTrue();
		verify(capabilityMapper).delete(any());
	}

	private MediaAccount account(String id, String mediaId) {
		MediaAccount account = new MediaAccount();
		account.setId(id);
		account.setMediaId(mediaId);
		return account;
	}

	private MediaChannel channel(String id, String mediaId, String status) {
		MediaChannel channel = new MediaChannel();
		channel.setId(id);
		channel.setMediaId(mediaId);
		channel.setStatus(status);
		return channel;
	}

}
