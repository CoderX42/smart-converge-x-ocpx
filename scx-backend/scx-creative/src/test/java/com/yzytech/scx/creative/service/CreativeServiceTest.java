package com.yzytech.scx.creative.service;

import com.yzytech.scx.commercial.service.OrganizationScopeService;
import com.yzytech.scx.creative.entity.CreativeAsset;
import com.yzytech.scx.creative.entity.CreativeReviewCase;
import com.yzytech.scx.creative.mapper.AssetBinaryMapper;
import com.yzytech.scx.creative.mapper.CreativeAssetMapper;
import com.yzytech.scx.creative.mapper.CreativeAssetRevisionMapper;
import com.yzytech.scx.creative.mapper.CreativeBindingMapper;
import com.yzytech.scx.creative.mapper.CreativeReviewCaseMapper;
import com.yzytech.scx.creative.mapper.CreativeReviewLogMapper;
import com.yzytech.scx.creative.mapper.LandingPageMapper;
import com.yzytech.scx.creative.mapper.LandingPageRevisionMapper;
import com.yzytech.scx.creative.port.AdGroupScopePort;
import com.yzytech.scx.subject.service.MediaScopeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreativeServiceTest {

	private final CreativeAssetMapper assetMapper = mock(CreativeAssetMapper.class);

	private final CreativeReviewCaseMapper reviewCaseMapper = mock(CreativeReviewCaseMapper.class);

	private final MediaScopeService mediaScopeService = mock(MediaScopeService.class);

	private final OrganizationScopeService organizationScopeService = mock(OrganizationScopeService.class);

	private final CreativeService service = new CreativeService(assetMapper, mock(CreativeAssetRevisionMapper.class),
			mock(AssetBinaryMapper.class), reviewCaseMapper, mock(CreativeReviewLogMapper.class),
			mock(LandingPageMapper.class), mock(LandingPageRevisionMapper.class), mock(CreativeBindingMapper.class),
			mediaScopeService, organizationScopeService, mock(AdGroupScopePort.class));

	@Test
	void assetRequiresBothMediaAndOrganizationScope() {
		CreativeAsset asset = asset();
		when(assetMapper.selectById("asset-1")).thenReturn(asset);
		when(mediaScopeService.canRead("media-1")).thenReturn(true);
		when(organizationScopeService.canRead("org-1")).thenReturn(false);

		assertThatThrownBy(() -> service.requireAsset("asset-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("资源不存在");
	}

	@Test
	void reviewCannotApproveBeforeCleanSecurityScan() {
		CreativeAsset asset = asset();
		CreativeReviewCase reviewCase = new CreativeReviewCase();
		reviewCase.setId("case-1");
		reviewCase.setAssetId("asset-1");
		reviewCase.setStatus("SUBMITTED");
		reviewCase.setVirusScanStatus("PENDING");
		when(reviewCaseMapper.selectById("case-1")).thenReturn(reviewCase);
		when(assetMapper.selectById("asset-1")).thenReturn(asset);
		when(mediaScopeService.canRead("media-1")).thenReturn(true);
		when(organizationScopeService.canRead("org-1")).thenReturn(true);

		assertThatThrownBy(() -> service.reviewAsset("case-1", "APPROVE", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("安全扫描");
	}

	@Test
	void duplicateAssetHashIsRejectedWithinTheSameScope() {
		when(mediaScopeService.canWrite("media-1")).thenReturn(true);
		when(assetMapper.selectOne(any())).thenReturn(asset());

		assertThatThrownBy(() -> service.createAsset("media-1", "org-1", "IMAGE", "banner", "https://oss.example/banner.jpg",
				"image/jpeg", 1024L, "same-hash", 1200, 628))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("相同图片已存在，请直接使用素材库中的已有版本");
	}

	private CreativeAsset asset() {
		CreativeAsset asset = new CreativeAsset();
		asset.setId("asset-1");
		asset.setMediaId("media-1");
		asset.setOrgId("org-1");
		asset.setStatus("REVIEWING");
		return asset;
	}

}
