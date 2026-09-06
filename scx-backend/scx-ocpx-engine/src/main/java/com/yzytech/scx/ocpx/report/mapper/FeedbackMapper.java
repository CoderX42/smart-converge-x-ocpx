package com.yzytech.scx.ocpx.report.mapper;

import com.yzytech.scx.ocpx.report.model.LogData;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Feedback 明细、日明细与临时报表的批量写入 Mapper。 */
public interface FeedbackMapper {

	/** 批量写入 Feedback 原始明细。 */
	void addLogFeedback(@Param("list") List<LogData> data);

	/** 按日和业务维度批量累加 Feedback 明细。 */
	void addLogFeedbackDay(@Param("list") List<LogData> data);

	/** 将 Feedback 指标批量累加到临时报表。 */
	void addReport(@Param("list") List<LogData> data);

	/** 按响应小时（yyyyMMddHH）删除 Feedback 日明细。 */
	void deleteFeedbackDay(@Param("time") Integer time);

	/** Delete at most {@code limit} historical rows before the current-day boundary. */
	int deleteFeedbackDayBefore(@Param("endTime") String endTime, @Param("limit") int limit);

}
