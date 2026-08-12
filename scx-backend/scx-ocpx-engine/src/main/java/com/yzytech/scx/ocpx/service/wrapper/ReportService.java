package com.yzytech.scx.ocpx.service.wrapper;

import com.yzytech.scx.ocpx.bean.LogData;

import java.util.List;

public interface ReportService {

	void addFeedback(List<LogData> data);

	void addCallback(List<LogData> data);

}
