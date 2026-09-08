package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.mapper.OcpxCreativeSqlMapper;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.CreativeRequests;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxCreativeMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMaterialMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxCreative;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.entity.SysMaterial;
import com.yzytech.scx.system.api.service.StorageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxCreativeServiceTest {

	private static final String ONE_PIXEL_WEBP = "UklGRhoAAABXRUJQVlA4TA4AAAAvAAAAAAcQEf0PRET/Aw==";

	private OcpxCreativeMapper creativeMapper;

	private OcpxMaterialMapper materialMapper;

	private StorageUploadService storageUploadService;

	private AdvertiserScope advertiserScope;

	private OcpxCreativeService service;

	@BeforeEach
	void setUp() {
		creativeMapper = mock(OcpxCreativeMapper.class);
		materialMapper = mock(OcpxMaterialMapper.class);
		OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);
		OcpxPromotionLinkMapper linkMapper = mock(OcpxPromotionLinkMapper.class);
		storageUploadService = mock(StorageUploadService.class);
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		advertiserScope = mock(AdvertiserScope.class);
		service = new OcpxCreativeService(creativeMapper, materialMapper, advertiserMapper, linkMapper,
				storageUploadService, XmlMapperTestSupport.mapper(OcpxCreativeSqlMapper.class, jdbcTemplate),
				advertiserScope);

		when(advertiserScope.constrainSelfOwned(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-1");
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		when(creativeMapper.selectList(any())).thenReturn(List.of());
		when(storageUploadService.uploadManaged(any(InputStream.class), anyString(), anyString(), anyLong()))
			.thenAnswer(invocation -> new ManagedStorageObject("storage-1", "local",
					"creative/" + invocation.getArgument(1), "https://assets.example/" + invocation.getArgument(1)));
		doAnswer(invocation -> {
			SysMaterial material = invocation.getArgument(0);
			material.setId("material-" + material.getName());
			return 1;
		}).when(materialMapper).insert(any(SysMaterial.class));
	}

	@Test
	void uploadParsesJpegPngAndWebpFromTheirRealFileHeaders() throws Exception {
		var jpeg = service.upload("advertiser-1", file("image.jpg", image("jpeg")));
		var png = service.upload("advertiser-1", file("image.png", image("png")));
		var webp = service.upload("advertiser-1", file("image.webp", Base64.getDecoder().decode(ONE_PIXEL_WEBP)));

		assertThat(jpeg.getMimeType()).isEqualTo("image/jpeg");
		assertThat(png.getMimeType()).isEqualTo("image/png");
		assertThat(webp.getMimeType()).isEqualTo("image/webp");
		assertThat(List.of(jpeg, png, webp)).allSatisfy(asset -> {
			assertThat(asset.getWidth()).isEqualTo(1);
			assertThat(asset.getHeight()).isEqualTo(1);
			assertThat(asset.getChecksumSha256()).hasSize(64);
		});
	}

	@Test
	void fakeExtensionIsRejectedBeforeStorageUpload() throws Exception {
		assertThatThrownBy(() -> service.upload("advertiser-1", file("fake.jpg", image("png"))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("扩展名与真实文件格式不一致");

		verify(storageUploadService, never()).uploadManaged(any(), anyString(), anyString(), anyLong());
	}

	@Test
	void oversizedCorruptAndOverDimensionImagesAreRejectedBeforeStorageUpload() throws Exception {
		assertThatThrownBy(() -> service.upload("advertiser-1", file("too-large.png", new byte[20 * 1024 * 1024 + 1])))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("20MB");
		assertThatThrownBy(() -> service.upload("advertiser-1", file("corrupt.png", new byte[] { 1, 2, 3 })))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("文件头或内容无效");
		assertThatThrownBy(() -> service.upload("advertiser-1", file("edge.png", pngHeader(8193, 1))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("8192px");
		assertThatThrownBy(() -> service.upload("advertiser-1", file("pixels.png", pngHeader(8001, 5000))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("4000 万");

		verify(storageUploadService, never()).uploadManaged(any(), anyString(), anyString(), anyLong());
	}

	@Test
	void createRejectsMaterialThatIsMissingOrOwnedByAnotherAdvertiser() {
		CreativeRequests.Save request = new CreativeRequests.Save("advertiser-1", "创意一", "material-other", 1, List.of(),
				null);

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不属于当前广告主");

		verify(creativeMapper, never()).insert(any(OcpxCreative.class));
	}

	@Test
	void materialRegistrationFailureDeletesTheStoredObject() throws Exception {
		ManagedStorageObject stored = new ManagedStorageObject("storage-1", "local", "creative/image.png",
				"https://assets.example/image.png");
		when(storageUploadService.uploadManaged(any(), anyString(), anyString(), anyLong())).thenReturn(stored);
		doThrow(new IllegalStateException("database unavailable")).when(materialMapper).insert(any(SysMaterial.class));

		assertThatThrownBy(() -> service.upload("advertiser-1", file("image.png", image("png"))))
			.isInstanceOf(IllegalStateException.class);

		verify(storageUploadService).delete(stored);
	}

	@Test
	void strictAdvertiserScopeRejectsCrossOwnerUploadBeforeStorage() {
		when(advertiserScope.constrainSelfOwned("advertiser-other")).thenThrow(new BootBusinessException("只能维护自身创意"));

		assertThatThrownBy(() -> service.upload("advertiser-other", file("image.png", new byte[] { 1 })))
			.isInstanceOf(BootBusinessException.class);

		verify(storageUploadService, never()).uploadManaged(any(), anyString(), anyString(), anyLong());
	}

	private MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, "application/octet-stream", content);
	}

	private byte[] image(String format) throws Exception {
		BufferedImage image = new BufferedImage(1, 1,
				"jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			assertThat(ImageIO.write(image, format, output)).isTrue();
			return output.toByteArray();
		}
	}

	private byte[] pngHeader(int width, int height) throws Exception {
		byte[] content = image("png");
		ByteBuffer.wrap(content, 16, 8).putInt(width).putInt(height);
		CRC32 crc = new CRC32();
		crc.update(content, 12, 17);
		ByteBuffer.wrap(content, 29, 4).putInt((int) crc.getValue());
		return content;
	}

}
