package com.yzytech.scx.commercial.dto;

import java.util.List;

public record AccountTreeView(String orgId, String orgName, String orgType, String status,
		List<AccountTreeView> children, List<AdAccountView> adAccounts) {
}
