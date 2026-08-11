package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.ocpx.admin.dto.BusinessCodeViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxEventDefinition;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventDefinitionMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcpxBusinessCodeService {

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxChannelMapper channelMapper;

	private final OcpxProductMapper productMapper;

	private final OcpxEventDefinitionMapper eventDefinitionMapper;

	public BusinessCodeViews.Generated generate(String resourceType) {
		ResourceType type = ResourceType.valueOf(resourceType.toUpperCase(Locale.ROOT));
		StpUtil.checkPermission(type.permission);
		for (int attempt = 0; attempt < 12; attempt++) {
			String code = type.prefix + token(type.uppercase);
			if (available(type, code)) {
				return new BusinessCodeViews.Generated(type.name(), code);
			}
		}
		throw new IllegalStateException("自动编码生成冲突，请重试或改为手动输入");
	}

	private boolean available(ResourceType type, String code) {
		return switch (type) {
			case ADVERTISER -> advertiserMapper
				.selectCount(Wrappers.<OcpxAdvertiser>lambdaQuery().eq(OcpxAdvertiser::getAdvertiserCode, code)) == 0;
			case CHANNEL -> channelMapper
				.selectCount(Wrappers.<OcpxChannel>lambdaQuery().eq(OcpxChannel::getChannelCode, code)) == 0;
			case PRODUCT ->
				productMapper.selectCount(Wrappers.<OcpxProduct>lambdaQuery().eq(OcpxProduct::getCode, code)) == 0;
			case EVENT -> eventDefinitionMapper.selectCount(
					Wrappers.<OcpxEventDefinition>lambdaQuery().eq(OcpxEventDefinition::getEventCode, code)) == 0;
		};
	}

	private String token(boolean uppercase) {
		String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		return uppercase ? token.toUpperCase(Locale.ROOT) : token.toLowerCase(Locale.ROOT);
	}

	private enum ResourceType {

		ADVERTISER("adv_", false, "ocpx:advertiser:add"), CHANNEL("chn_", false, "ocpx:channel:write"),
		PRODUCT("prd_", false, "ocpx:product:add"), EVENT("EVT_", true, "ocpx:event:add");

		private final String prefix;

		private final boolean uppercase;

		private final String permission;

		ResourceType(String prefix, boolean uppercase, String permission) {
			this.prefix = prefix;
			this.uppercase = uppercase;
			this.permission = permission;
		}

	}

}
