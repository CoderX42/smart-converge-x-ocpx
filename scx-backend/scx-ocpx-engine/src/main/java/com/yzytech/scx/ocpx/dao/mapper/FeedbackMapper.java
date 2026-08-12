package com.yzytech.scx.ocpx.dao.mapper;

import com.yzytech.scx.ocpx.bean.LogData;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FeedbackMapper {

	void addLogFeedback(@Param("list") List<LogData> data);

	void addLogFeedbackDay(@Param("list") List<LogData> data);

	void addReport(@Param("list") List<LogData> data);

}
