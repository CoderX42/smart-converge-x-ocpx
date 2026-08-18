package com.yzytech.scx.ocpx.admin.dto;

import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public final class ProtocolConfigViews {

	private ProtocolConfigViews() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {

		private OcpxUpstreamProtocol protocol;

		private List<String> allowedHosts = new ArrayList<>();

	}

}
