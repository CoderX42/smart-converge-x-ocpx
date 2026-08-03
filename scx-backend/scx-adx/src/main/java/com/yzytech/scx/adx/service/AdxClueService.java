package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.CatalogRequests;
import com.yzytech.scx.adx.entity.AdxClue;
import com.yzytech.scx.adx.mapper.AdxClueMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class AdxClueService {

	private static final long MAX_UPLOAD_SIZE = 10 * 1024 * 1024;

	private static final Set<Integer> STATUSES = Set.of(0, 1, 2);

	private final AdxClueMapper clueMapper;

	private final AtomicReference<TaskState> task = new AtomicReference<>(TaskState.idle());

	public Page<AdxClue> page(Page<AdxClue> page, String keyword, String cluePhone, Integer sourceType,
			Integer clueStatus, LocalDate startDate, LocalDate endDate) {
		return clueMapper.selectPage(page,
				Wrappers.<AdxClue>lambdaQuery()
					.and(StringUtils.hasText(keyword),
							q -> q.like(AdxClue::getClueName, keyword).or().like(AdxClue::getClueIndex, keyword))
					.like(StringUtils.hasText(cluePhone), AdxClue::getCluePhone, cluePhone)
					.eq(sourceType != null, AdxClue::getSourceType, sourceType)
					.eq(clueStatus != null, AdxClue::getClueStatus, clueStatus)
					.ge(startDate != null, AdxClue::getCreateTime, startDate == null ? null : startDate.atStartOfDay())
					.lt(endDate != null, AdxClue::getCreateTime,
							endDate == null ? null : endDate.plusDays(1).atStartOfDay())
					.orderByDesc(AdxClue::getCreateTime));
	}

	@Transactional
	public AdxClue save(String id, CatalogRequests.SaveClue request) {
		if (!STATUSES.contains(request.clueStatus())) {
			throw new IllegalArgumentException("线索状态无效");
		}
		AdxClue entity = StringUtils.hasText(id) ? require(id) : new AdxClue();
		entity.setSourceType(request.sourceType());
		entity.setClueStatus(request.clueStatus());
		entity.setClueName(trimToNull(request.clueName()));
		entity.setCluePhone(request.cluePhone().trim());
		entity.setClueIndex(trimToNull(request.clueIndex()));
		entity.setSonTaskId(trimToNull(request.sonTaskId()));
		entity.setClueCityId(trimToNull(request.clueCityId()));
		entity.setClueAreaId(trimToNull(request.clueAreaId()));
		entity.setClueBrandId(trimToNull(request.clueBrandId()));
		entity.setClueCarSeriesId(trimToNull(request.clueCarSeriesId()));
		entity.setClueCarModelId(trimToNull(request.clueCarModelId()));
		entity.setFirstCarRegistrationTime(request.firstCarRegistrationTime());
		entity.setCarMileage(request.carMileage());
		entity.setRemark(trimToNull(request.remark()));
		if (StringUtils.hasText(id)) {
			clueMapper.updateById(entity);
		}
		else {
			clueMapper.insert(entity);
		}
		return entity;
	}

	@Transactional
	public int upload(MultipartFile file) throws IOException {
		validateFile(file);
		task.set(TaskState.running("正在解析线索文件"));
		int imported = 0;
		try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
			Sheet sheet = workbook.getSheetAt(0);
			Map<String, Integer> headers = headers(sheet.getRow(sheet.getFirstRowNum()));
			DataFormatter formatter = new DataFormatter();
			for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
				Row row = sheet.getRow(index);
				if (row == null) {
					continue;
				}
				String phone = cell(row, headers, formatter, "手机号", "线索手机号", "cluePhone");
				if (!phone.matches("^1\\d{10}$")) {
					continue;
				}
				String clueIndex = cell(row, headers, formatter, "线索编号", "线索索引", "clueIndex");
				if (exists(phone, clueIndex)) {
					continue;
				}
				AdxClue clue = new AdxClue();
				clue.setSourceType(1);
				clue.setClueStatus(0);
				clue.setCluePhone(phone);
				clue.setClueName(cell(row, headers, formatter, "姓名", "线索姓名", "clueName"));
				clue.setClueIndex(clueIndex);
				clue.setSonTaskId(cell(row, headers, formatter, "三级子任务", "子任务ID", "sonTaskId"));
				clue.setClueCityId(cell(row, headers, formatter, "城市ID", "clueCityId"));
				clue.setClueAreaId(cell(row, headers, formatter, "区县ID", "clueAreaId"));
				clue.setClueBrandId(cell(row, headers, formatter, "品牌ID", "clueBrandId"));
				clue.setClueCarSeriesId(cell(row, headers, formatter, "车系ID", "clueCarSeriesId"));
				clue.setClueCarModelId(cell(row, headers, formatter, "车型ID", "clueCarModelId"));
				clue.setFirstCarRegistrationTime(
						dateCell(row, headers, formatter, "上牌时间", "首次上牌时间", "firstCarRegistrationTime"));
				clue.setCarMileage(decimalCell(row, headers, formatter, "行驶里程", "里程", "carMileage"));
				clueMapper.insert(clue);
				imported++;
			}
			task.set(TaskState.completed("导入完成，共新增 " + imported + " 条线索"));
			return imported;
		}
		catch (RuntimeException | IOException e) {
			task.set(TaskState.failed("线索文件解析失败"));
			throw e;
		}
	}

	@Transactional
	public int batchStatus(CatalogRequests.BatchClueStatus request) {
		if (!STATUSES.contains(request.status())) {
			throw new IllegalArgumentException("线索状态无效");
		}
		AdxClue update = new AdxClue();
		update.setClueStatus(request.status());
		if (request.ids() != null && !request.ids().isEmpty()) {
			return clueMapper.update(update, Wrappers.<AdxClue>lambdaUpdate().in(AdxClue::getId, request.ids()));
		}
		if (!"FILTER".equalsIgnoreCase(request.mode()) || request.sourceStatus() == null || request.startDate() == null
				|| request.endDate() == null) {
			throw new IllegalArgumentException("请选择线索或提供原状态和日期范围");
		}
		if (!STATUSES.contains(request.sourceStatus()) || request.startDate().isAfter(request.endDate())) {
			throw new IllegalArgumentException("批量筛选条件无效");
		}
		return clueMapper.update(update,
				Wrappers.<AdxClue>lambdaUpdate()
					.eq(AdxClue::getClueStatus, request.sourceStatus())
					.eq(request.sourceType() != null, AdxClue::getSourceType, request.sourceType())
					.like(StringUtils.hasText(request.cluePhone()), AdxClue::getCluePhone, request.cluePhone())
					.and(StringUtils.hasText(request.keyword()),
							q -> q.like(AdxClue::getClueName, request.keyword())
								.or()
								.like(AdxClue::getClueIndex, request.keyword()))
					.ge(AdxClue::getCreateTime, request.startDate().atStartOfDay())
					.lt(AdxClue::getCreateTime, request.endDate().plusDays(1).atStartOfDay()));
	}

	@Transactional
	public Map<String, Object> submitPending() {
		long pending = clueMapper.selectCount(Wrappers.<AdxClue>lambdaQuery().eq(AdxClue::getClueStatus, 0));
		TaskState state = pending == 0 ? TaskState.completed("没有待上报线索")
				: TaskState.blocked("已保留 " + pending + " 条待上报线索；需配置汽车之家任务凭据后才能安全发送");
		task.set(state);
		return status();
	}

	public Map<String, Object> status() {
		TaskState state = task.get();
		long pending = clueMapper.selectCount(Wrappers.<AdxClue>lambdaQuery().eq(AdxClue::getClueStatus, 0));
		Map<String, Object> result = new HashMap<>();
		result.put("processing", "RUNNING".equals(state.status()));
		result.put("pending", pending);
		result.put("status", state.status());
		result.put("message", state.message());
		return result;
	}

	private void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("请选择线索文件");
		}
		if (file.getSize() > MAX_UPLOAD_SIZE) {
			throw new IllegalArgumentException("线索文件不能超过 10MB");
		}
		String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
		if (!filename.endsWith(".xls") && !filename.endsWith(".xlsx")) {
			throw new IllegalArgumentException("仅支持 xls 或 xlsx 文件");
		}
	}

	private Map<String, Integer> headers(Row header) {
		if (header == null) {
			throw new IllegalArgumentException("线索文件缺少表头");
		}
		DataFormatter formatter = new DataFormatter();
		Map<String, Integer> result = new HashMap<>();
		for (int index = header.getFirstCellNum(); index < header.getLastCellNum(); index++) {
			result.put(formatter.formatCellValue(header.getCell(index)).trim(), index);
		}
		return result;
	}

	private String cell(Row row, Map<String, Integer> headers, DataFormatter formatter, String... names) {
		for (String name : names) {
			Integer index = headers.get(name);
			if (index != null) {
				return formatter.formatCellValue(row.getCell(index)).trim();
			}
		}
		return "";
	}

	private LocalDateTime dateCell(Row row, Map<String, Integer> headers, DataFormatter formatter, String... names) {
		Cell target = findCell(row, headers, names);
		if (target == null) {
			return null;
		}
		if (DateUtil.isCellDateFormatted(target)) {
			return target.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
		}
		String value = formatter.formatCellValue(target).trim();
		for (String pattern : List.of("yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd")) {
			try {
				return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern)).atStartOfDay();
			}
			catch (DateTimeParseException ignored) {
				// Try the next supported source format.
			}
		}
		return null;
	}

	private BigDecimal decimalCell(Row row, Map<String, Integer> headers, DataFormatter formatter, String... names) {
		Cell target = findCell(row, headers, names);
		if (target == null) {
			return null;
		}
		String value = formatter.formatCellValue(target).replace(",", "").replace("万公里", "").trim();
		try {
			return StringUtils.hasText(value) ? new BigDecimal(value) : null;
		}
		catch (NumberFormatException ignored) {
			return null;
		}
	}

	private Cell findCell(Row row, Map<String, Integer> headers, String... names) {
		for (String name : names) {
			Integer index = headers.get(name);
			if (index != null) {
				return row.getCell(index);
			}
		}
		return null;
	}

	private boolean exists(String phone, String clueIndex) {
		return clueMapper.selectCount(Wrappers.<AdxClue>lambdaQuery()
			.eq(AdxClue::getCluePhone, phone)
			.eq(StringUtils.hasText(clueIndex), AdxClue::getClueIndex, clueIndex)) > 0;
	}

	private AdxClue require(String id) {
		AdxClue entity = clueMapper.selectById(id);
		if (entity == null) {
			throw new IllegalArgumentException("线索不存在");
		}
		return entity;
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private record TaskState(String status, String message, LocalDateTime updatedAt) {

		static TaskState idle() {
			return new TaskState("IDLE", "等待任务", LocalDateTime.now());
		}

		static TaskState running(String message) {
			return new TaskState("RUNNING", message, LocalDateTime.now());
		}

		static TaskState completed(String message) {
			return new TaskState("COMPLETED", message, LocalDateTime.now());
		}

		static TaskState blocked(String message) {
			return new TaskState("BLOCKED", message, LocalDateTime.now());
		}

		static TaskState failed(String message) {
			return new TaskState("FAILED", message, LocalDateTime.now());
		}
	}

}
