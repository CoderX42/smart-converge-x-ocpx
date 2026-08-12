package com.yzytech.scx.ocpx.report.mapper;

import com.yzytech.scx.ocpx.report.model.LogData;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Callback 明细与临时报表的批量写入 Mapper。 */
public interface CallbackMapper {

	/** 批量写入 Callback 明细事实。 */
	void addLogCallback(@Param("list") List<LogData> data);

	/** 将 Callback 指标批量累加到临时报表。 */
	void addReport(@Param("list") List<LogData> data);

}
