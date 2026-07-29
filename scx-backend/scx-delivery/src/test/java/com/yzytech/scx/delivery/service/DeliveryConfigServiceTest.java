package com.yzytech.scx.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.delivery.entity.MediaChannel;
import com.yzytech.scx.delivery.entity.Partner;
import com.yzytech.scx.delivery.entity.Product;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.entity.TrackingLink;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.IntegrationDocumentMapper;
import com.yzytech.scx.delivery.mapper.DeliveryConfigRevisionMapper;
import com.yzytech.scx.delivery.mapper.DeliveryStatusHistoryMapper;
import com.yzytech.scx.delivery.mapper.LinkEventMappingMapper;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.delivery.mapper.PartnerMapper;
import com.yzytech.scx.delivery.mapper.ProductMapper;
import com.yzytech.scx.delivery.mapper.RtaEndpointMapper;
import com.yzytech.scx.delivery.mapper.TrackingLinkMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DeliveryConfigServiceTest {

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final TrackingLinkMapper linkMapper = mock(TrackingLinkMapper.class);

	private final PartnerMapper partnerMapper = mock(PartnerMapper.class);

	private final ProductMapper productMapper = mock(ProductMapper.class);

	private final MediaChannelMapper channelMapper = mock(MediaChannelMapper.class);

	private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);

	private final RtaEndpointMapper rtaEndpointMapper = mock(RtaEndpointMapper.class);

	private final TaskScopeService taskScopeService = mock(TaskScopeService.class);

	private final DeliveryConfigService service = new DeliveryConfigService(scopeService, mock(MasterDataService.class),
			partnerMapper, productMapper, mock(IntegrationDocumentMapper.class), channelMapper, linkMapper,
			mock(LinkEventMappingMapper.class), taskMapper, rtaEndpointMapper, taskScopeService,
			mock(DeliveryConfigRevisionMapper.class), mock(DeliveryStatusHistoryMapper.class),
			new ObjectMapper().findAndRegisterModules());

	@Test
	void newLinkUsesPartnerOnlyAndIgnoresLegacyCustomerInput() {
		TrackingLink link = link(null, "customer-request");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(productMapper.selectById("product-1")).thenReturn(product());
		when(partnerMapper.selectById("partner-1")).thenReturn(partner());
		when(linkMapper.insert(link)).thenReturn(1);

		assertThat(service.saveLink(link)).isTrue();

		assertThat(link.getCustomerId()).isNull();
		verify(linkMapper).insert(link);
	}

	@Test
	void linkUpdatePreservesHistoricalCustomerReference() {
		TrackingLink existing = link("link-1", "customer-history");
		TrackingLink update = link("link-1", "customer-request");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(linkMapper.selectById("link-1")).thenReturn(existing);
		when(productMapper.selectById("product-1")).thenReturn(product());
		when(partnerMapper.selectById("partner-1")).thenReturn(partner());
		when(linkMapper.updateById(update)).thenReturn(1);

		assertThat(service.saveLink(update)).isTrue();

		assertThat(update.getCustomerId()).isEqualTo("customer-history");
		verify(linkMapper).updateById(update);
	}

	@Test
	void refusesTaskActivationWhenBoundRtaWasDisabled() {
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setMediaId("media-1");
		task.setLinkId("link-1");
		task.setChannelId("channel-1");
		task.setRtaEnabled(true);
		task.setRtaEndpointId("rta-1");
		task.setStatus("DRAFT");
		task.setVersion(0);
		TrackingLink link = new TrackingLink();
		link.setStatus("ACTIVE");
		MediaChannel channel = new MediaChannel();
		channel.setStatus("ACTIVE");
		RtaEndpoint endpoint = new RtaEndpoint();
		endpoint.setMediaId("media-1");
		endpoint.setStatus("DISABLED");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(taskScopeService.canWrite(task)).thenReturn(true);
		when(taskMapper.selectById("task-1")).thenReturn(task);
		when(linkMapper.selectById("link-1")).thenReturn(link);
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		when(rtaEndpointMapper.selectById("rta-1")).thenReturn(endpoint);

		assertThatThrownBy(() -> service.changeTaskStatus("task-1", "ACTIVE", "RTA 已禁用", 0))
			.hasMessage("启用任务前绑定的 RTA 配置必须仍处于启用状态");
	}

	private TrackingLink link(String id, String customerId) {
		TrackingLink value = new TrackingLink();
		value.setId(id);
		value.setMediaId("media-1");
		value.setCode("LINK-1");
		value.setName("链接一");
		value.setProductId("product-1");
		value.setPartnerId("partner-1");
		value.setCustomerId(customerId);
		value.setDeviceType("ANDROID");
		value.setTargetUrl("http://localhost/landing");
		value.setConfigVersion(1);
		value.setStatus("DRAFT");
		value.setVersion(0);
		return value;
	}

	private Partner partner() {
		Partner value = new Partner();
		value.setId("partner-1");
		value.setMediaId("media-1");
		return value;
	}

	private Product product() {
		Product value = new Product();
		value.setId("product-1");
		value.setMediaId("media-1");
		value.setPartnerId("partner-1");
		value.setCustomerId("customer-history");
		return value;
	}

}
