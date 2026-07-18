package com.yzytech.scx.delivery.service;

import com.yzytech.scx.delivery.mapper.IntegrationDocumentMapper;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.delivery.mapper.PartnerMapper;
import com.yzytech.scx.delivery.mapper.ProductMapper;
import com.yzytech.scx.delivery.mapper.UpstreamCustomerMapper;
import com.yzytech.scx.delivery.entity.UpstreamCustomer;
import com.yzytech.scx.subject.service.MediaScopeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterDataServiceTest {
    private final MediaScopeService scopeService = mock(MediaScopeService.class);
    private final UpstreamCustomerMapper customerMapper = mock(UpstreamCustomerMapper.class);
    private final MasterDataService service = new MasterDataService(scopeService, mock(PartnerMapper.class), customerMapper,
            mock(ProductMapper.class), mock(IntegrationDocumentMapper.class), mock(MediaChannelMapper.class));

    @Test
    void sandboxUrlAllowsOnlyExplicitLocalHosts() {
        assertThatCode(() -> service.validateSandboxUrl("http://localhost/landing"))
            .doesNotThrowAnyException();
        assertThatCode(() -> service.validateSandboxUrl("https://callback-mock/callback"))
            .doesNotThrowAnyException();
    }

    @Test
    void sandboxUrlRejectsCredentialsAndExternalHosts() {
        assertThatThrownBy(() -> service.validateSandboxUrl("https://user:pass@localhost/callback"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("仅允许沙箱 allowlist URL");
        assertThatThrownBy(() -> service.validateSandboxUrl("https://example.com/callback"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("仅允许沙箱 allowlist URL");
    }

    @Test
    void savesCustomerOnlyWithinWritableMediaScope() {
        UpstreamCustomer customer = new UpstreamCustomer();
        customer.setMediaId("media-1");
        customer.setCode("C-1");
        customer.setName("客户一");
        when(scopeService.canWrite("media-1")).thenReturn(true);
        when(customerMapper.insert(customer)).thenReturn(1);

        service.saveCustomer(customer);

        assertThatCode(() -> { if (!"ACTIVE".equals(customer.getStatus())) throw new AssertionError(); }).doesNotThrowAnyException();
        verify(customerMapper).insert(customer);
    }
}
