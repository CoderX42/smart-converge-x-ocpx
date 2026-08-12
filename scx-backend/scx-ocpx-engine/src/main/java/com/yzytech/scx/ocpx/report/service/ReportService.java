package com.yzytech.scx.ocpx.report.service;

import com.yzytech.scx.ocpx.report.model.LogData;

import java.util.List;

/** Redis 队列批次写入明细表和临时报表的服务契约。 */
public interface ReportService {

	/** 按既定顺序写入 Feedback 明细、日明细和临时报表。 */
	void addFeedback(List<LogData> data);

	/** 按既定顺序写入 Callback 明细和临时报表。 */
	void addCallback(List<LogData> data);

}
