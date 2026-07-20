package com.yzytech.scx.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class SysDictionaryRouteContractTest {

	@Test
	void supportsCanonicalAndLegacyConsoleRoutesForSystemResources() {
		assertRoutes(SysDictController.class, "/dict", "/system/dict");
		assertRoutes(SysDictValueController.class, "/dictvalue", "/system/dictvalue");
		assertRoutes(SysRoleController.class, "/sysrole", "/system/sysrole");
		assertRoutes(SysConfigController.class, "/config", "/system/config");
		assertRoutes(SysRegionController.class, "/region", "/system/region");
		assertRoutes(SysArticleController.class, "/article", "/system/article");
		assertRoutes(SysServerController.class, "/sysserver", "/system/sysserver");
		assertRoutes(SysMaterialGroupController.class, "/materialgroup", "/system/materialgroup");
		assertRoutes(SysArticleCategoryController.class, "/article-category", "/system/article-category");
		assertRoutes(SysLogController.class, "/syslog", "/system/syslog");
		assertRoutes(SysDeptController.class, "/sysdept", "/system/sysdept");
		assertRoutes(SysAdLaunchController.class, "/ad-launch", "/system/ad-launch");
		assertRoutes(SysSmsConfigController.class, "/sms-config", "/system/sms-config");
		assertRoutes(SysMaterialController.class, "/material", "/system/material");
		assertRoutes(SysOnlineUserController.class, "/onlineuser", "/system/onlineuser");
		assertRoutes(SysLoginLogController.class, "/sysloginlog", "/system/sysloginlog");
		assertRoutes(SysAgreementController.class, "/agreement", "/system/agreement");
		assertRoutes(SysStorageConfigController.class, "/storage-config", "/system/storage-config");
		assertRoutes(SysSocialAccountController.class, "/social-account", "/system/social-account");
		assertRoutes(SysSmsSendRecordController.class, "/sms-send-record", "/system/sms-send-record");
		assertRoutes(SysUploadController.class, "/file", "/system/file");
	}

	private void assertRoutes(Class<?> controller, String... expectedRoutes) {
		RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
		assertThat(mapping).isNotNull();
		assertThat(Arrays.asList(mapping.value())).containsExactlyInAnyOrder(expectedRoutes);
	}

}
