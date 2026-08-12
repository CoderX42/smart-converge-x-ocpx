package com.yzytech.scx.ocpx.dao.mapper;

import com.yzytech.scx.ocpx.bean.LogData;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CallbackMapper {

	void addLogCallback(@Param("list") List<LogData> data);

	void addReport(@Param("list") List<LogData> data);

}
