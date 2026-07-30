package com.yzytech.scx.reporting.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.reporting.dto.CreateBackfillRequest;
import com.yzytech.scx.reporting.entity.ReportBackfill;
import com.yzytech.scx.reporting.mapper.ReportBackfillMapper;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.mapper.MediaMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportBackfillService {

	private final ReportBackfillMapper mapper;

	private final ReportPipelineService pipelineService;

	private final MediaScopeService scopeService;

	private final MediaMapper mediaMapper;

	private final ObjectMapper objectMapper;

	public ReportBackfill create(CreateBackfillRequest request) {
		if (request == null || request.getMediaId() == null || !scopeService.canRead(request.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		if (request.getStartDate() == null || request.getEndDate() == null
				|| request.getStartDate().isAfter(request.getEndDate()))
			throw new IllegalArgumentException("请选择有效的补算日期范围");
		if (request.getStartDate().plusDays(366).isBefore(request.getEndDate()))
			throw new IllegalArgumentException("单次补算最多 366 天");
		ReportBackfill task = new ReportBackfill();
		task.setMediaId(request.getMediaId());
		task.setStartDate(request.getStartDate());
		task.setEndDate(request.getEndDate());
		task.setDryRun(request.isDryRun());
		task.setStatus("PENDING");
		task.setProcessedDates(0);
		task.setOldRowCount(0L);
		task.setNewRowCount(0L);
		task.setRequestedBy(SecurityUtils.getUser() == null ? "system" : SecurityUtils.getUser().getUserId());
		mapper.insert(task);
		return task;
	}

	public ReportBackfill get(String id) {
		ReportBackfill task = mapper.selectById(id);
		if (task == null || !scopeService.canRead(task.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		return task;
	}

	@Scheduled(fixedDelay = 30_000)
	public void processPending() {
		ReportBackfill task = mapper.selectOne(Wrappers.<ReportBackfill>lambdaQuery()
			.eq(ReportBackfill::getStatus, "PENDING")
			.orderByAsc(ReportBackfill::getCreateTime)
			.last("LIMIT 1"));
		if (task == null)
			return;
		if (mapper.claim(task.getId()) != 1)
			return;
		process(task);
	}

	void process(ReportBackfill task) {
		task.setStatus("RUNNING");
		task.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
		mapper.updateById(task);
		try {
			long oldRows = mapper.countCurrentRows(task.getMediaId(), task.getStartDate(), task.getEndDate());
			ZoneId reportZone = reportZone(task.getMediaId());
			LocalDateTime startUtc = utcStart(task.getStartDate(), reportZone);
			LocalDateTime endUtc = utcStart(task.getEndDate().plusDays(1), reportZone);
			long sourceRows = mapper.countRawEvents(task.getMediaId(), startUtc, endUtc);
			long mediaFactRows = mapper.countMediaFacts(task.getMediaId(), startUtc, endUtc);
			task.setOldRowCount(oldRows);
			if (!Boolean.TRUE.equals(task.getDryRun())) {
				LocalDate date = task.getStartDate();
				long newRows = 0;
				int processed = 0;
				while (!date.isAfter(task.getEndDate())) {
					newRows += pipelineService.refreshSystem(date, task.getMediaId(), "BACKFILL").getRowCount();
					processed++;
					task.setProcessedDates(processed);
					mapper.updateById(task);
					date = date.plusDays(1);
				}
				task.setNewRowCount(newRows);
			}
			task.setDifferenceJson(json(Map.of("oldReportRows", oldRows, "sourceEventRows", sourceRows,
					"sourceMediaFactRows", mediaFactRows, "newReportRows", task.getNewRowCount())));
			task.setStatus("SUCCEEDED");
			task.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
			mapper.updateById(task);
		}
		catch (RuntimeException exception) {
			task.setStatus("FAILED");
			task.setErrorMessage(exception.getMessage());
			task.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
			mapper.updateById(task);
		}
	}

	private LocalDateTime utcStart(LocalDate date, ZoneId zone) {
		return date.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	private ZoneId reportZone(String mediaId) {
		Media media = mediaMapper.selectById(mediaId);
		try {
			return ZoneId.of(media == null || media.getTimezone() == null || media.getTimezone().isBlank()
					? "Asia/Shanghai" : media.getTimezone());
		}
		catch (RuntimeException ignored) {
			return ZoneId.of("Asia/Shanghai");
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("补算差异无法序列化", exception);
		}
	}

}
