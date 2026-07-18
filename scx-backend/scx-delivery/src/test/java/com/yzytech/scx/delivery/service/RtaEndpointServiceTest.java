package com.yzytech.scx.delivery.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.mapper.RtaEndpointMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import org.junit.jupiter.api.Test;

class RtaEndpointServiceTest {
    private final RtaEndpointMapper mapper = mock(RtaEndpointMapper.class);
    private final MediaScopeService scopeService = mock(MediaScopeService.class);
    private final MasterDataService masterDataService = mock(MasterDataService.class);
    private final RtaEndpointService service = new RtaEndpointService(mapper, scopeService, masterDataService);

    @Test
    void createsOnlyWithinWritableMediaScope() {
        RtaEndpoint endpoint = endpoint();
        when(scopeService.canWrite("media-1")).thenReturn(true);
        when(mapper.insert(endpoint)).thenReturn(1);

        service.save(endpoint);

        verify(masterDataService).validateSandboxUrl("http://localhost:8080/sandbox/rta");
        verify(mapper).insert(endpoint);
    }

    @Test
    void rejectsRtaWhenMediaScopeIsNotWritable() {
        when(scopeService.canWrite("media-1")).thenReturn(false);

        assertThatThrownBy(() -> service.save(endpoint())).hasMessage("资源不存在");
    }

    private RtaEndpoint endpoint() {
        RtaEndpoint endpoint = new RtaEndpoint();
        endpoint.setMediaId("media-1");
        endpoint.setCode("RTA-1");
        endpoint.setName("sandbox rta");
        endpoint.setEndpointUrl("http://localhost:8080/sandbox/rta");
        return endpoint;
    }
}
