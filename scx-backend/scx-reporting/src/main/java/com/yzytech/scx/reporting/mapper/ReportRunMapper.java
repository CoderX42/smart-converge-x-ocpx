package com.yzytech.scx.reporting.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.reporting.entity.ReportRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface ReportRunMapper extends BaseMapper<ReportRun> {
    @Select("""
        SELECT r.*
        FROM scx_report_run r
        WHERE r.report_date = #{date} AND r.status = 'SUCCEEDED'
          AND EXISTS (
            SELECT 1 FROM scx_report_daily d
            WHERE d.report_run_id = r.id AND d.media_id = #{mediaId}
          )
        ORDER BY r.finished_at DESC
        LIMIT 1
        """)
    ReportRun selectLatestForMedia(@Param("date") LocalDate date, @Param("mediaId") String mediaId);
}
