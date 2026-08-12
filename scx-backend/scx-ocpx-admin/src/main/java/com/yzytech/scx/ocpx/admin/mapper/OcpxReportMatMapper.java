package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.admin.model.OcpxReportMat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OcpxReportMatMapper extends BaseMapper<OcpxReportMat> {

	List<OcpxReportMat> groupReportMinute(@Param("groupTime") String groupTime, @Param("mark") Integer mark);

	void deleteReportMatTemp(@Param("groupTime") String groupTime, @Param("mark") Integer mark);

	List<OcpxReportMat> groupReport(@Param("groupTime") String groupTime, @Param("mark") Integer mark);

}
