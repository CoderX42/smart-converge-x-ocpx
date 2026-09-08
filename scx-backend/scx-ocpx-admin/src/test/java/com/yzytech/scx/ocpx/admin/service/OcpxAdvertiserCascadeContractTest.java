package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OcpxAdvertiserCascadeContractTest {

	@Test
	void mapperCoversEveryAdvertiserOwnedBusinessAndHistoryTableWithoutDeletingSharedMedia() throws Exception {
		Map<String, String> deleteTargets = new LinkedHashMap<>();
		deleteTargets.put("deleteReportMatTemp", "scx_ocpx_report_mat_temp");
		deleteTargets.put("deleteReportMat", "scx_ocpx_report_mat");
		deleteTargets.put("deleteCallbackLogs", "scx_ocpx_log_callback");
		deleteTargets.put("deleteFeedbackDayLogs", "scx_ocpx_log_feedback_day");
		deleteTargets.put("deleteFeedbackLogs", "scx_ocpx_log_feedback");
		deleteTargets.put("deleteFlowLogs", "scx_ocpx_flow_log");
		deleteTargets.put("deleteCreativeBindings", "scx_ocpx_creative_promotion_link");
		deleteTargets.put("deleteCreatives", "scx_ocpx_creative");
		deleteTargets.put("deleteDeductionRules", "scx_ocpx_deduction_rule");
		deleteTargets.put("deleteMonitorTasks", "scx_ocpx_monitor_task");
		deleteTargets.put("deletePromotionLinks", "scx_ocpx_promotion_link");

		StringBuilder cascadeSql = new StringBuilder();
		for (Map.Entry<String, String> target : deleteTargets.entrySet()) {
			Method method = OcpxAdvertiserMapper.class.getMethod(target.getKey(), String.class);
			String sql = sql(method.getName());
			assertThat(sql).contains(target.getValue());
			cascadeSql.append(sql).append('\n');
		}
		assertThat(cascadeSql).doesNotContain("DELETE FROM scx_ocpx_media", "DELETE m FROM scx_ocpx_media");
	}

	@Test
	void deleteLocksAdvertiserAndCollectsAllBusinessRouteKeysBeforeCascade() throws Exception {
		Method lock = OcpxAdvertiserMapper.class.getMethod("selectByIdForUpdate", String.class);
		assertThat(sql(lock.getName())).containsIgnoringCase("FOR UPDATE");

		Method routes = OcpxAdvertiserMapper.class.getMethod("selectCascadeRoutes", String.class);
		assertThat(sql(routes.getName())).contains("scx_ocpx_monitor_task", "scx_ocpx_deduction_rule",
				"scx_ocpx_promotion_link");
		assertThat(OcpxAdvertiserService.class.getMethod("delete", String.class).getAnnotation(Transactional.class))
			.isNotNull();
	}

	private String sql(String method) {
		return XmlMapperTestSupport.configuration(OcpxAdvertiserMapper.class)
			.getMappedStatement(OcpxAdvertiserMapper.class.getName() + "." + method)
			.getBoundSql(Map.of("id", "adv-1"))
			.getSql();
	}

}
