package com.yzytech.scx.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.yzytech.scx.delivery.dto.DpConversionRequest;
import com.yzytech.scx.delivery.dto.DpConversionResult;
import com.yzytech.scx.delivery.mapper.ShortLinkMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import org.junit.jupiter.api.Test;

class ShortLinkServiceTest {
    private final ShortLinkService service = new ShortLinkService(
            mock(ShortLinkMapper.class), mock(MediaScopeService.class), mock(MasterDataService.class));

    @Test
    void normalizesTaobaoDpUrlAndRetainsOnlyVendorParameters() {
        DpConversionResult result = service.convertDp(request(
                "TAOBAO_DP", "https://uland.taobao.com/coupon/edetail?pid=mm_1&itemId=123"));

        assertThat(result.targetUrl()).isEqualTo("https://uland.taobao.com/coupon/edetail?itemId=123&pid=mm_1");
        assertThat(result.sourceUrl()).contains("pid=mm_1");
    }

    @Test
    void rejectsExternalAndUnsafeDpRedirects() {
        assertThatThrownBy(() -> service.convertDp(request("TAOBAO_DP", "https://example.com/?itemId=123")))
                .hasMessage("仅允许淘宝 DP 白名单 HTTPS 地址");
        assertThatThrownBy(() -> service.convertDp(request("JD_DP", "https://item.jd.com/1.html?scx_redirect=x")))
                .hasMessage("DP 推广参数不合法");
    }

    private DpConversionRequest request(String type, String sourceUrl) {
        DpConversionRequest request = new DpConversionRequest();
        request.setLinkType(type);
        request.setSourceUrl(sourceUrl);
        return request;
    }
}
