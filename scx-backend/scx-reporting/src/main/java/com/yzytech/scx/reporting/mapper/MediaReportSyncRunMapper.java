package com.yzytech.scx.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.reporting.entity.MediaReportSyncRun;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MediaReportSyncRunMapper extends BaseMapper<MediaReportSyncRun> {

	@Update("""
			UPDATE scx_media_report_sync_run
			SET locked_by=#{owner},locked_until=#{lockedUntil},status='RUNNING',
			    error_code=NULL,error_message=NULL,started_at=NOW(3),finished_at=NULL
			WHERE id=#{id}
			  AND status NOT IN ('SUCCEEDED','SUCCEEDED_EMPTY')
			  AND (locked_until IS NULL OR locked_until<NOW(3) OR locked_by=#{owner})
			""")
	int tryAcquire(@Param("id") String id, @Param("owner") String owner,
			@Param("lockedUntil") LocalDateTime lockedUntil);

}
