package com.yzytech.scx.reporting.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.reporting.entity.ExportTask;
import com.yzytech.scx.reporting.entity.ReportDaily;
import com.yzytech.scx.reporting.mapper.ExportTaskMapper;
import com.yzytech.scx.reporting.mapper.ReportDailyMapper;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import com.yzytech.scx.tracking.dto.AttributionReviewView;
import com.yzytech.scx.tracking.mapper.AttributionReviewMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExportTaskProcessor {

	static final int MAX_ROWS = 100_000;

	private final ExportTaskMapper exportTaskMapper;

	private final ReportDailyMapper reportDailyMapper;

	private final AttributionReviewMapper attributionReviewMapper;

	private final UserSubjectScopeMapper scopeMapper;

	@Async("hxAsyncExecutor")
	@Transactional
	public void process(String id) {
		LocalDateTime now = LocalDateTime.now();
		if (exportTaskMapper.claim(id, now) != 1) {
			return;
		}
		ExportTask task = exportTaskMapper.selectById(id);
		try {
			if (!hasScope(task.getRequesterUserId(), task.getMediaId())) {
				fail(task, "SCOPE_REVOKED", "导出执行时媒体范围已失效");
				return;
			}
			List<String> taskIds = taskIds(task.getTaskScopeSnapshot());
			ExportContent content = export(task, taskIds);
			task.setStatus("SUCCEEDED");
			task.setRowCount(content.rowCount());
			task.setArtifactName(content.fileName());
			task.setArtifactContent(content.csv());
			task.setArtifactSha256(SignUtils.sha256(new String(content.csv(), StandardCharsets.UTF_8)));
			task.setExpiresAt(LocalDateTime.now().plusHours(24));
			task.setFinishedAt(LocalDateTime.now());
			exportTaskMapper.updateById(task);
		}
		catch (RowLimitExceededException exception) {
			fail(task, "ROW_LIMIT_EXCEEDED", "导出结果超过 100000 行限制");
		}
		catch (Exception exception) {
			fail(task, "EXPORT_FAILED", "导出处理失败");
		}
	}

	@Scheduled(fixedDelay = 60_000)
	public void expireArtifacts() {
		exportTaskMapper.expireCompleted(LocalDateTime.now());
	}

	private boolean hasScope(String userId, String mediaId) {
		if (exportTaskMapper.hasAdminRole(userId) > 0) {
			return true;
		}
		return scopeMapper.selectCount(Wrappers.<UserSubjectScope>lambdaQuery()
			.eq(UserSubjectScope::getUserId, userId)
			.eq(UserSubjectScope::getSubjectType, "MEDIA")
			.eq(UserSubjectScope::getSubjectId, mediaId)) > 0;
	}

	private List<String> taskIds(String snapshot) {
		if (snapshot == null || "*".equals(snapshot))
			return List.of();
		return Arrays.stream(snapshot.split(",")).filter(value -> !value.isBlank()).toList();
	}

	private boolean taskScopeRestricted(String snapshot) {
		return snapshot != null && !"*".equals(snapshot);
	}

	private void fail(ExportTask task, String code, String message) {
		task.setStatus("FAILED");
		task.setErrorCode(code);
		task.setErrorMessage(message);
		task.setFinishedAt(LocalDateTime.now());
		exportTaskMapper.updateById(task);
	}

	private ExportContent export(ExportTask task, List<String> taskIds) {
		boolean taskRestricted = taskScopeRestricted(task.getTaskScopeSnapshot());
		if ("REDUCTION_CSV".equals(task.getExportType())) {
			List<AttributionReviewView> rows = attributionReviewMapper.selectReductionForExport(task.getMediaId(),
					task.getReportStartDate(), task.getReportEndDate(), MAX_ROWS + 1, taskRestricted, taskIds);
			verifyRowLimit(rows.size());
			return new ExportContent(rows.size(),
					"scx-reduction-" + task.getReportStartDate() + "-" + task.getReportEndDate() + ".csv",
					toReductionCsv(rows));
		}
		List<ReportDaily> rows = reportDailyMapper.selectForExport(task.getMediaId(), task.getReportStartDate(),
				task.getReportEndDate(), MAX_ROWS + 1, taskRestricted, taskIds);
		verifyRowLimit(rows.size());
		return new ExportContent(rows.size(),
				"scx-daily-" + task.getReportStartDate() + "-" + task.getReportEndDate() + ".csv", toCsv(rows));
	}

	private void verifyRowLimit(int rowCount) {
		if (rowCount > MAX_ROWS) {
			throw new RowLimitExceededException();
		}
	}

	private byte[] toCsv(List<ReportDaily> rows) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		StringBuilder content = new StringBuilder(
				"报告日期,时区,媒体ID,媒体账户ID,计划ID,合作方ID,产品ID,链接ID,任务ID," + "媒体曝光,媒体点击,追踪点击,有效点击,去重点击,媒体转化,归因激活,归因注册,归因付费,归因下单,"
						+ "次留,3留,7留,首活,首单,拉活,自定义,授信,回传成功,回传失败,消耗,币种,Revision,数据状态,质量码,来源水位,计算时间\n");
		for (ReportDaily row : rows) {
			content.append(csv(row.getReportDate()))
				.append(',')
				.append(csv(row.getTimezone()))
				.append(',')
				.append(csv(row.getMediaId()))
				.append(',')
				.append(csv(row.getMediaAccountId()))
				.append(',')
				.append(csv(row.getCampaignId()))
				.append(',')
				.append(csv(row.getPartnerId()))
				.append(',')
				.append(csv(row.getProductId()))
				.append(',')
				.append(csv(row.getLinkId()))
				.append(',')
				.append(csv(row.getTaskId()))
				.append(',')
				.append(row.getMediaImpressionCount())
				.append(',')
				.append(row.getMediaClickCount())
				.append(',')
				.append(row.getUniqueClickCount())
				.append(',')
				.append(row.getEligibleClickCount())
				.append(',')
				.append(row.getDeduplicatedClickCount())
				.append(',')
				.append(row.getMediaConversionCount())
				.append(',')
				.append(row.getAttributedActivateCount())
				.append(',')
				.append(row.getAttributedRegisterCount())
				.append(',')
				.append(row.getAttributedPayCount())
				.append(',')
				.append(row.getAttributedOrderCount())
				.append(',')
				.append(row.getNextDayReturnCount())
				.append(',')
				.append(row.getRetention3dCount())
				.append(',')
				.append(row.getRetention7dCount())
				.append(',')
				.append(row.getFirstActivateCount())
				.append(',')
				.append(row.getFirstOrderCount())
				.append(',')
				.append(row.getReactivateCount())
				.append(',')
				.append(row.getCustomCount())
				.append(',')
				.append(row.getCreditCount())
				.append(',')
				.append(row.getCallbackSucceededCount())
				.append(',')
				.append(row.getCallbackFailedCount())
				.append(',')
				.append(row.getSpendAmount())
				.append(',')
				.append(csv(row.getCurrency()))
				.append(',')
				.append(row.getRevision())
				.append(',')
				.append(csv(row.getDataStatus()))
				.append(',')
				.append(csv(row.getQualityCode()))
				.append(',')
				.append(csv(row.getSourceWatermark()))
				.append(',')
				.append(csv(row.getCalculatedAt()))
				.append('\n');
		}
		output.writeBytes(content.toString().getBytes(StandardCharsets.UTF_8));
		return output.toByteArray();
	}

	private byte[] toReductionCsv(List<AttributionReviewView> rows) {
		StringBuilder content = new StringBuilder(
				"归因ID,任务编码,任务名称,转化事件,转化时间,点击归因时间,归因状态,归因原因,是否超量,审核状态,审减原因,审核人,审核时间,归因完成时间\n");
		for (AttributionReviewView row : rows) {
			content.append(csv(row.getId()))
				.append(',')
				.append(csv(row.getTaskCode()))
				.append(',')
				.append(csv(row.getTaskName()))
				.append(',')
				.append(csv(row.getConversionEventType()))
				.append(',')
				.append(csv(row.getConversionOccurredAt()))
				.append(',')
				.append(csv(row.getClickOccurredAt()))
				.append(',')
				.append(csv(row.getAttributionStatus()))
				.append(',')
				.append(csv(row.getAttributionReason()))
				.append(',')
				.append(Boolean.TRUE.equals(row.getOverLimit()) ? "是" : "否")
				.append(',')
				.append(csv(row.getReviewStatus()))
				.append(',')
				.append(csv(row.getReviewReason()))
				.append(',')
				.append(csv(row.getReviewedBy()))
				.append(',')
				.append(csv(row.getReviewedAt()))
				.append(',')
				.append(csv(row.getAttributedAt()))
				.append('\n');
		}
		return content.toString().getBytes(StandardCharsets.UTF_8);
	}

	private String csv(Object value) {
		if (value == null)
			return "";
		return '"' + String.valueOf(value).replace("\"", "\"\"") + '"';
	}

	private record ExportContent(int rowCount, String fileName, byte[] csv) {
	}

	private static class RowLimitExceededException extends RuntimeException {

	}

}
