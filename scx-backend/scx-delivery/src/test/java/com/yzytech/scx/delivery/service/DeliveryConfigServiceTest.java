package com.yzytech.scx.delivery.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yzytech.scx.delivery.entity.MediaChannel;
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
import com.yzytech.scx.delivery.mapper.UpstreamCustomerMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DeliveryConfigServiceTest {
    private final MediaScopeService scopeService = mock(MediaScopeService.class);
    private final TrackingLinkMapper linkMapper = mock(TrackingLinkMapper.class);
    private final MediaChannelMapper channelMapper = mock(MediaChannelMapper.class);
    private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);
    private final RtaEndpointMapper rtaEndpointMapper = mock(RtaEndpointMapper.class);
    private final TaskScopeService taskScopeService = mock(TaskScopeService.class);
    private final DeliveryConfigService service = new DeliveryConfigService(scopeService, mock(MasterDataService.class),
            mock(PartnerMapper.class), mock(UpstreamCustomerMapper.class), mock(ProductMapper.class), mock(IntegrationDocumentMapper.class), channelMapper,
            linkMapper, mock(LinkEventMappingMapper.class), taskMapper, rtaEndpointMapper,
            taskScopeService, mock(DeliveryConfigRevisionMapper.class), mock(DeliveryStatusHistoryMapper.class), new ObjectMapper().findAndRegisterModules());

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
}
